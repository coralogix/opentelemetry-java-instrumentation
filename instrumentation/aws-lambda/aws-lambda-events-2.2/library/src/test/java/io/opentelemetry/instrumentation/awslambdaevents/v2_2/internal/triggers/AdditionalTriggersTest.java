package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.RPC_REQUEST_PAYLOAD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.CognitoEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal.SerializationUtil;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdditionalTriggersTest {

  private static final AttributeKey<String> FAAS_TRIGGER = AttributeKey.stringKey("faas.trigger");
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");

  @Test
  void eventBridgeTriggerExtractsExpectedAttributes() {
    ScheduledEvent event =
        new ScheduledEvent()
            .withSource("aws.events")
            .withDetailType("Scheduled Event")
            .withRegion("eu-west-1");

    EventBridgeTrigger trigger = new EventBridgeTrigger();
    AwsLambdaRequest request = request(event);

    assertThat(trigger.matches(request)).isTrue();
    assertThat(trigger.extract(request)).isEqualTo("Scheduled Event");

    Attributes attributes = onStart(trigger, request);
    assertThat(attributes.get(FAAS_TRIGGER)).isEqualTo("timer");
    assertThat(attributes.get(FAAS_TRIGGER_TYPE)).isEqualTo("EventBridge");
    assertThat(attributes.get(RPC_REQUEST_PAYLOAD)).contains("Scheduled Event");
  }

  @Test
  void kinesisTriggerExtractsExpectedAttributes() {
    String json =
        "{\n"
            + "  \"Records\": [\n"
            + "    {\n"
            + "      \"kinesis\": {\n"
            + "        \"partitionKey\": \"1\",\n"
            + "        \"sequenceNumber\": \"1\",\n"
            + "        \"data\": \"SGVsbG8=\",\n"
            + "        \"approximateArrivalTimestamp\": 1545084650.987\n"
            + "      },\n"
            + "      \"eventSource\": \"aws:kinesis\",\n"
            + "      \"eventVersion\": \"1.0\",\n"
            + "      \"eventID\": \"shardId-000000000000:1\",\n"
            + "      \"eventName\": \"aws:kinesis:record\",\n"
            + "      \"invokeIdentityArn\": \"arn:aws:iam::123456789012:role/lambda-role\",\n"
            + "      \"awsRegion\": \"eu-west-1\",\n"
            + "      \"eventSourceARN\": \"arn:aws:kinesis:eu-west-1:123456789012:stream/test-stream\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    KinesisEvent event = SerializationUtil.fromJson(json, KinesisEvent.class);

    KinesisTrigger trigger = new KinesisTrigger();
    AwsLambdaRequest request = request(event);

    assertThat(trigger.matches(request)).isTrue();
    assertThat(trigger.extract(request)).isEqualTo("test-stream deliver");

    Attributes attributes = onStart(trigger, request);
    assertThat(attributes.get(FAAS_TRIGGER)).isEqualTo("pubsub");
    assertThat(attributes.get(FAAS_TRIGGER_TYPE)).isEqualTo("Kinesis");
    assertThat(attributes.get(MESSAGING_SYSTEM)).isEqualTo("AmazonKinesis");
    assertThat(attributes.get(MESSAGING_OPERATION)).isEqualTo("deliver");
    assertThat(attributes.get(MESSAGING_DESTINATION_NAME)).isEqualTo("test-stream");
    assertThat(attributes.get(RPC_REQUEST_PAYLOAD)).contains("aws:kinesis:record");
  }

  @Test
  void snsTriggerExtractsExpectedAttributes() {
    String json =
        "{\n"
            + "  \"Records\": [\n"
            + "    {\n"
            + "      \"EventVersion\": \"1.0\",\n"
            + "      \"EventSubscriptionArn\": \"arn:aws:sns:eu-west-1:123456789012:test:sub\",\n"
            + "      \"EventSource\": \"aws:sns\",\n"
            + "      \"Sns\": {\n"
            + "        \"SignatureVersion\": \"1\",\n"
            + "        \"Timestamp\": \"2019-01-02T12:45:07.000Z\",\n"
            + "        \"Signature\": \"sig\",\n"
            + "        \"SigningCertUrl\": \"https://sns.eu-west-1.amazonaws.com/test.pem\",\n"
            + "        \"MessageId\": \"95df01b4-ee98-5cb9-9903-4c221d41eb5e\",\n"
            + "        \"Message\": \"Hello from SNS!\",\n"
            + "        \"MessageAttributes\": {},\n"
            + "        \"Type\": \"Notification\",\n"
            + "        \"TopicArn\":\"arn:aws:sns:eu-west-1:123456789012:test-topic\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    SNSEvent event = SerializationUtil.fromJson(json, SNSEvent.class);

    SnsTrigger trigger = new SnsTrigger();
    AwsLambdaRequest request = request(event);

    assertThat(trigger.matches(request)).isTrue();
    assertThat(trigger.extract(request)).isEqualTo("arn:aws:sns:eu-west-1:123456789012:test-topic deliver");

    Attributes attributes = onStart(trigger, request);
    assertThat(attributes.get(FAAS_TRIGGER)).isEqualTo("pubsub");
    assertThat(attributes.get(FAAS_TRIGGER_TYPE)).isEqualTo("SNS");
    assertThat(attributes.get(MESSAGING_SYSTEM)).isEqualTo("AmazonSNS");
    assertThat(attributes.get(MESSAGING_OPERATION)).isEqualTo("deliver");
    assertThat(attributes.get(MESSAGING_DESTINATION_NAME))
        .isEqualTo("arn:aws:sns:eu-west-1:123456789012:test-topic");
    assertThat(attributes.get(RPC_REQUEST_PAYLOAD)).contains("Hello from SNS!");
  }

  @Test
  void cognitoTriggerExtractsExpectedAttributes() {
    String json =
        "{\n"
            + "  \"region\": \"eu-west-1\",\n"
            + "  \"identityPoolId\": \"eu-west-1:test-pool\",\n"
            + "  \"identityId\": \"eu-west-1:identity\",\n"
            + "  \"datasetName\": \"profile\",\n"
            + "  \"eventType\": \"SyncTrigger\",\n"
            + "  \"version\": 1,\n"
            + "  \"datasetRecords\": {}\n"
            + "}";
    CognitoEvent event = SerializationUtil.fromJson(json, CognitoEvent.class);

    CognitoTrigger trigger = new CognitoTrigger();
    AwsLambdaRequest request = request(event);

    assertThat(trigger.matches(request)).isTrue();
    assertThat(trigger.extract(request)).isEqualTo("SyncTrigger");

    Attributes attributes = onStart(trigger, request);
    assertThat(attributes.get(FAAS_TRIGGER)).isEqualTo("other");
    assertThat(attributes.get(FAAS_TRIGGER_TYPE)).isEqualTo("Cognito");
    assertThat(attributes.get(RPC_REQUEST_PAYLOAD)).contains("SyncTrigger");
  }

  @Test
  void stepFunctionsTriggerExtractsExpectedAttributes() {
    Map<String, Object> event = new HashMap<>();
    Map<String, Object> executionContext = new HashMap<>();
    executionContext.put("Id", "exec-1");
    executionContext.put("Name", "execution-name");
    Map<String, Object> stateContext = new HashMap<>();
    stateContext.put("Name", "Invoke Lambda function");
    Map<String, Object> stateMachineContext = new HashMap<>();
    stateMachineContext.put("Id", "sm-1");
    stateMachineContext.put("Name", "TriggerStateMachine");
    Map<String, Object> input = new HashMap<>();
    input.put("key", "value");

    event.put("executionContext", executionContext);
    event.put("stateContext", stateContext);
    event.put("stateMachineContext", stateMachineContext);
    event.put("input", input);

    StepFunctionsTrigger trigger = new StepFunctionsTrigger();
    AwsLambdaRequest request = request(event);

    assertThat(trigger.matches(request)).isTrue();
    assertThat(trigger.extract(request)).isEqualTo("TriggerStateMachine Invoke Lambda function");

    Attributes attributes = onStart(trigger, request);
    assertThat(attributes.get(FAAS_TRIGGER)).isEqualTo("other");
    assertThat(attributes.get(FAAS_TRIGGER_TYPE)).isEqualTo("Step Functions");
    assertThat(attributes.get(RPC_REQUEST_PAYLOAD)).contains("executionContext");
  }

  @Test
  void dynamodbTriggerStillExtractsExpectedAttributes() {
    String json =
        "{\n"
            + "  \"Records\": [\n"
            + "    {\n"
            + "      \"eventID\": \"1\",\n"
            + "      \"eventName\": \"INSERT\",\n"
            + "      \"eventVersion\": \"1.1\",\n"
            + "      \"eventSource\": \"aws:dynamodb\",\n"
            + "      \"awsRegion\": \"eu-west-1\",\n"
            + "      \"dynamodb\": {\n"
            + "        \"ApproximateCreationDateTime\": 1428537600,\n"
            + "        \"Keys\": {\n"
            + "          \"test\": {\"S\": \"value\"}\n"
            + "        },\n"
            + "        \"SequenceNumber\": \"111\",\n"
            + "        \"SizeBytes\": 26,\n"
            + "        \"StreamViewType\": \"NEW_AND_OLD_IMAGES\"\n"
            + "      },\n"
            + "      \"eventSourceARN\": \"arn:aws:dynamodb:eu-west-1:123456789012:table/test/stream/2026-01-01T00:00:00.000\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    DynamodbEvent event = SerializationUtil.fromJson(json, DynamodbEvent.class);

    DynamoDBTrigger trigger = new DynamoDBTrigger();
    AwsLambdaRequest request = request(event);

    assertThat(trigger.matches(request)).isTrue();
    assertThat(trigger.extract(request)).isEqualTo("INSERT");

    Attributes attributes = onStart(trigger, request);
    assertThat(attributes.get(FAAS_TRIGGER)).isEqualTo("datasource");
    assertThat(attributes.get(FAAS_TRIGGER_TYPE)).isEqualTo("DynamoDB");
    assertThat(attributes.get(RPC_REQUEST_PAYLOAD)).contains("INSERT");
  }

  private static AwsLambdaRequest request(Object input) {
    Context awsContext = mock(Context.class);
    return AwsLambdaRequest.create(awsContext, input, new HashMap<String, String>());
  }

  private static Attributes onStart(
      Trigger trigger,
      AwsLambdaRequest request) {
    AttributesBuilder builder = Attributes.builder();
    trigger.onStart(builder, io.opentelemetry.context.Context.root(), request);
    return builder.build();
  }
}
