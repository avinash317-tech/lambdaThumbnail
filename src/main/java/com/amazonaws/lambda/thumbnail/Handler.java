package com.amazonaws.lambda.thumbnail;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Handler implements RequestHandler<Object, ResponseModel> {

	private ResponseModel responseModel;
	private AWSCredentials credentials = new BasicAWSCredentials("access key",
			"secret key");
	private AmazonS3 s3Client = new AmazonS3Client(credentials);
	final float MAX_WIDTH = 100;
	final float MAX_HEIGHT = 100;
	final String JPG_TYPE = (String) "jpg";
	final String JPG_MIME = (String) "image/jpeg";
	final String PNG_TYPE = (String) "png";
	final String PNG_MIME = (String) "image/png";

	@Override
	public ResponseModel handleRequest(Object req, Context context) {

		try {
			context.getLogger().log("Input: " + req);
			List<String> list = new ArrayList<>();
			JsonObject jsonObject = convertObjectToJsonObject(req);
			String originalImgName = jsonObject.get("imgName").getAsString();
			String originalImgBucket = jsonObject.get("bucket").getAsString();
			String image = jsonObject.get("image").getAsString();
			String thumbnailImgBucket = originalImgBucket + "-resized";
			String thumbnailImgName = "resized-" + originalImgName;
			String imageType = match(originalImgName);
			byte[] buffer = decoder(image);
			originalImage(buffer, originalImgBucket, originalImgName);

			list.add("image path : " + originalImgBucket + "/" + originalImgName);

			// Download the image from S3 into a stream
			S3Object s3Object = s3Client.getObject(new GetObjectRequest(originalImgBucket, originalImgName));
			InputStream objectData = s3Object.getObjectContent();
			BufferedImage srcImage = ImageIO.read(objectData);
			BufferedImage thumbnail = getThumbnailImage(srcImage);
			uploadThumbnailImage(thumbnail, imageType, thumbnailImgBucket, thumbnailImgName);
			list.add("thumbnail image path : " + thumbnailImgBucket + "/" + thumbnailImgName);
			responseModel = new ResponseModel("Success", list);
			return responseModel;
		} catch (Exception e) {
			e.printStackTrace();
			return responseModel = new ResponseModel("failed ", e.getMessage());
		}
	}

	// pattern matching
	private String match(String originalImgName) {
		// Infer the image type.
		Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(originalImgName);
		if (!matcher.matches()) {
			System.out.println("Unable to infer image type for key " + originalImgName);
		}
		String imageType = matcher.group(1);
		System.out.println(imageType);
		if (!(JPG_TYPE.equals(imageType)) && !(PNG_TYPE.equals(imageType))) {
			System.out.println("Skipping non-image " + originalImgName);
		}

		return imageType;
	}

	// Convert Object to JsonObject
	private JsonObject convertObjectToJsonObject(Object object) {
		Gson gson = new Gson();
		JsonElement jsonElement = gson.toJsonTree(object);
		JsonObject jsonObject = (JsonObject) jsonElement;
		return jsonObject;
	}

	private BufferedImage getThumbnailImage(BufferedImage srcImage) {
		BufferedImage thumbImg = Scalr.resize(srcImage, Method.QUALITY, Mode.AUTOMATIC, 50, 50, Scalr.OP_ANTIALIAS);
		return thumbImg;
	}

	private void uploadThumbnailImage(BufferedImage thumbnail, String imageType, String thumbnailImgBucket,
			String thumbnailImgName) throws IOException {
		// Re-encode image to target format
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(thumbnail, imageType, os);
		InputStream is = new ByteArrayInputStream(os.toByteArray());
		// Set Content-Length and Content-Type
		ObjectMetadata meta = new ObjectMetadata();
		meta.setContentLength(os.size());

		if (JPG_TYPE.equals(imageType)) {
			meta.setContentType(JPG_MIME);
		}
		if (PNG_TYPE.equals(imageType)) {
			meta.setContentType(PNG_MIME);
		}

		s3Client.putObject(thumbnailImgBucket, thumbnailImgName, is, meta);
	}

	private void originalImage(byte buffer[], String originalImgBucket, String originalImgName) {
		ObjectMetadata objectMetadata = new ObjectMetadata();
		objectMetadata.setContentLength(buffer.length);
		PutObjectRequest putObjectRequest = new PutObjectRequest(originalImgBucket, originalImgName,
				new ByteArrayInputStream(buffer), objectMetadata);
		s3Client.putObject(putObjectRequest);
	}

	private byte[] decoder(String imageInString) {
		byte[] imageByteArray = Base64.getDecoder().decode(imageInString);
		return imageByteArray;
	}

}
