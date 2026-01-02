using Microsoft.AspNetCore.Mvc;
using Newtonsoft.Json;
using Confluent.Kafka;
using System.Text.Json;

var builder = WebApplication.CreateBuilder(args);

// Configure logging
builder.Logging.ClearProviders();
builder.Logging.AddConsole();
builder.Logging.AddJsonConsole();

// Add services
builder.Services.AddControllers();
builder.Services.AddSingleton<LoanItemService>();
builder.Services.AddSingleton<KafkaProducerService>();

var app = builder.Build();

app.MapControllers();
app.MapGet("/health", () => Results.Ok(new { status = "healthy", service = "loan-router" }));

app.Run();

// Models
public class LoanRequest
{
    [JsonPropertyName("request_id")]
    public string? RequestId { get; set; }
    
    [JsonPropertyName("loan_type")]
    public string? LoanType { get; set; }
    
    [JsonPropertyName("loan_requested_value")]
    public double LoanRequestedValue { get; set; }
    
    [JsonPropertyName("loan_item")]
    public string? LoanItem { get; set; }
    
    [JsonPropertyName("loan_item_name")]
    public string? LoanItemName { get; set; }
    
    [JsonPropertyName("customer_id")]
    public string? CustomerId { get; set; }
    
    [JsonPropertyName("partner_name")]
    public string? PartnerName { get; set; }
    
    [JsonPropertyName("timestamp")]
    public string? Timestamp { get; set; }
}

// Loan Item Service
public class LoanItemService
{
    private readonly HashSet<string> _validItems;
    private readonly ILogger<LoanItemService> _logger;
    
    public LoanItemService(ILogger<LoanItemService> logger)
    {
        _logger = logger;
        _validItems = new HashSet<string>
        {
            "CAR-001", "CAR-002", "CAR-003", "CAR-004", "CAR-005",
            "HOUSE-001", "HOUSE-002", "HOUSE-003", "HOUSE-004", "HOUSE-005"
        };
        
        _logger.LogInformation("LoanItemService initialized with {Count} valid items", _validItems.Count);
    }
    
    public bool ItemExists(string itemId)
    {
        return _validItems.Contains(itemId);
    }
}

// Kafka Producer Service
public class KafkaProducerService : IDisposable
{
    private readonly IProducer<string, string> _producer;
    private readonly ILogger<KafkaProducerService> _logger;
    
    public KafkaProducerService(ILogger<KafkaProducerService> logger, IConfiguration configuration)
    {
        _logger = logger;
        
        var kafkaBootstrapServers = configuration["KAFKA_BOOTSTRAP_SERVERS"] ?? "kafka:9092";
        
        var config = new ProducerConfig
        {
            BootstrapServers = kafkaBootstrapServers,
            ClientId = "loan-router"
        };
        
        _producer = new ProducerBuilder<string, string>(config).Build();
        _logger.LogInformation("Kafka producer initialized with bootstrap servers: {Servers}", kafkaBootstrapServers);
    }
    
    public async Task<bool> SendToKafka(string topic, string key, string message)
    {
        try
        {
            var result = await _producer.ProduceAsync(topic, new Message<string, string>
            {
                Key = key,
                Value = message
            });
            
            _logger.LogInformation("Message sent to Kafka topic {Topic}, partition {Partition}, offset {Offset}",
                result.Topic, result.Partition.Value, result.Offset.Value);
            
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error sending message to Kafka topic {Topic}", topic);
            return false;
        }
    }
    
    public void Dispose()
    {
        _producer?.Flush(TimeSpan.FromSeconds(10));
        _producer?.Dispose();
    }
}

// Controller
[ApiController]
[Route("[controller]")]
public class RouteController : ControllerBase
{
    private readonly LoanItemService _loanItemService;
    private readonly KafkaProducerService _kafkaService;
    private readonly ILogger<RouteController> _logger;
    
    public RouteController(
        LoanItemService loanItemService,
        KafkaProducerService kafkaService,
        ILogger<RouteController> logger)
    {
        _loanItemService = loanItemService;
        _kafkaService = kafkaService;
        _logger = logger;
    }
    
    [HttpPost]
    public async Task<IActionResult> RouteLoan([FromBody] LoanRequest request)
    {
        var requestId = request.RequestId ?? "unknown";
        
        _logger.LogInformation("Routing loan request {RequestId} of type {LoanType}",
            requestId, request.LoanType);
        
        // Determine risk level based on item existence
        string riskLevel = "low_risk";
        bool itemExists = false;
        
        if (!string.IsNullOrEmpty(request.LoanItem))
        {
            itemExists = _loanItemService.ItemExists(request.LoanItem);
            if (!itemExists)
            {
                riskLevel = "high_risk";
                _logger.LogWarning("Loan item {LoanItem} not found in database - flagged as high risk",
                    request.LoanItem);
            }
        }
        
        // Add risk level to request
        var enrichedRequest = new
        {
            request.RequestId,
            request.LoanType,
            request.LoanRequestedValue,
            request.LoanItem,
            request.LoanItemName,
            request.CustomerId,
            request.PartnerName,
            request.Timestamp,
            RiskLevel = riskLevel,
            ItemExists = itemExists
        };
        
        // Determine Kafka topic based on loan type
        string topic = request.LoanType switch
        {
            "personal" => "loans-personal",
            "real_state" => "loans-real-state",
            "vehicle" => "loans-vehicle",
            _ => "loans-unknown"
        };
        
        // Serialize and send to Kafka
        string message = JsonConvert.SerializeObject(enrichedRequest);
        bool sent = await _kafkaService.SendToKafka(topic, requestId, message);
        
        // Log for Dynatrace bizevent transformation
        _logger.LogInformation(
            "KAFKA_ROUTE: request_id={RequestId}, loan_type={LoanType}, " +
            "topic={Topic}, risk_level={RiskLevel}, item_exists={ItemExists}, " +
            "loan_value={LoanValue}, customer_id={CustomerId}",
            requestId, request.LoanType, topic, riskLevel, itemExists,
            request.LoanRequestedValue, request.CustomerId);
        
        if (!sent)
        {
            return StatusCode(500, new { status = "error", message = "Failed to send to Kafka" });
        }
        
        return Ok(new
        {
            status = "success",
            request_id = requestId,
            topic = topic,
            risk_level = riskLevel
        });
    }
}
