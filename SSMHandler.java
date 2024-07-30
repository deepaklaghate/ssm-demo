package com.example.demo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.gson.Gson;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

public class SSMHandler implements RequestHandler<CloudFormationCustomResourceEvent, String> {
	
	public static final AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
	public static final SsmClient ssmClient = SsmClient.builder().region(Region.US_EAST_1).build();

	@Override
	public String handleRequest(CloudFormationCustomResourceEvent event, Context context) {
		
		String status = "SUCCESS";
		String reason = "Operation Successful";
		
		if ("Create".equals(event.getRequestType()) || "Update".equals(event.getRequestType())) {	
			GetParameterRequest parameterRequest = GetParameterRequest.builder().name("Demo").build();
			GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
			String ssmParmvalue = parameterResponse.parameter().value();
			
			InputStream inputStream = new ByteArrayInputStream(ssmParmvalue.getBytes(StandardCharsets.UTF_8));
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(ssmParmvalue.length());
			
			s3Client.putObject(new PutObjectRequest("my-bucket", "ssmKey.csv", inputStream, metadata));
		
			System.out.println("FILE CREATED SUCCESSFULLY IN S3 BUCKET");
			
		} else if ("Delete".equals(event.getRequestType())) {
			reason = "Deletion Operation Successful";
        }
		
		sendResponse(event, status, reason);

		return "SSM code executed successfully";
	}
	
	private void sendResponse(CloudFormationCustomResourceEvent event, String status, String reason) {
    
		Gson gson = new Gson();
	    String eventJson = gson.toJson(event);
	    Map map = gson.fromJson(eventJson, Map.class);
	    
	    String physicalResourceId = "ssm-"+UUID.randomUUID().toString();
	    
	    String putEndpoint = (String)map.get("ResponseURL");
	    String stackId = (String)map.get("StackId");
	    String requestId = (String)map.get("RequestId");
	    String logicalResourceId = (String)map.get("LogicalResourceId");
	    
	    /* Building response */
        String responseJson = "{\"Status\":\"" + status + "\",\"Reason\":\"" + reason + "\",\"PhysicalResourceId\":\"" + physicalResourceId + "\",\"StackId\":\"" + stackId + "\",\"RequestId\":\"" + requestId + "\",\"LogicalResourceId\":\"" + logicalResourceId + "\",\"NoEcho\":false,\"Data\":{\"Key\":\"Value\"}}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(putEndpoint))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(responseJson))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        /* Sending Response */
        System.out.println("Sending Response to stack, response code: ");

		try {
			client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (Exception e) {
			e.printStackTrace();
		}
        System.out.println("Finish sending signal");
    }
}