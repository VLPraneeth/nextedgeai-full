package com.syncari.core.service;

import static java.lang.String.format;

import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class EncryptionService {
	private static final String KEY = "SYNC2SomeRandomeHa$hSHA512";
	private static final String DELIMITER = ":";
	private static final String AES_CBC_PKCS5_PADDING = "AES/CBC/PKCS5Padding";
	private static final String UTF_8 = "UTF-8";
	private static SecretKeySpec secretKey = null;

	public String encrypt(String value) {
		return encrypt(value, KEY);
	}

	public String decrypt(String value) {
		return decrypt(value, KEY);
	}

	public Optional<String> decryptIfPossible(String value) {
		if(StringUtils.isBlank(value)){
			return Optional.empty();
		}
		try {
			return Optional.ofNullable(decrypt(value, KEY));
		}catch(Exception e){
			return Optional.empty();
		}
	}

	public String encrypt(String value, String key) {
		if (StringUtils.isBlank(value))
			throw new RuntimeException("Value cannot be empty for encryption");
		boolean error = false;
		try {
			String decrypted = decrypt(value, key);
			if (!StringUtils.isBlank(decrypted))
				error = true;
		} catch (Exception e) {
		}
		if (error)
			throw new RuntimeException("Value already encrypted");
		try {
			SecretKeySpec secretKey = createSecretKey(key);
			Cipher pbeCipher = Cipher.getInstance(AES_CBC_PKCS5_PADDING);
			pbeCipher.init(Cipher.ENCRYPT_MODE, secretKey);
			AlgorithmParameters parameters = pbeCipher.getParameters();
			IvParameterSpec ivParameterSpec = parameters.getParameterSpec(IvParameterSpec.class);
			byte[] cryptoText = pbeCipher.doFinal(value.getBytes(UTF_8));
			byte[] iv = ivParameterSpec.getIV();
			return format("%s%s%s", base64Encode(iv), DELIMITER, base64Encode(cryptoText));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public Optional<String> encryptIfPossible(String value) {
		if(StringUtils.isBlank(value)){
			return Optional.empty();
		}
		return Optional.of(encrypt(value));
	}


	public String decrypt(String value, String key) {
		if (StringUtils.isBlank(value))
			throw new RuntimeException("Value cannot be empty for decryption");
		try {
			SecretKeySpec secretKey = createSecretKey(key);
			Cipher pbeCipher = Cipher.getInstance(AES_CBC_PKCS5_PADDING);
			String[] parts = value.split(DELIMITER);
			if (parts.length < 2) {
				throw new RuntimeException("Trying to decrypt a value which is not encrypted");
			}
			String iv = parts[0];
			String property = value.split(DELIMITER)[1];
			pbeCipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(base64Decode(iv)));
			return new String(pbeCipher.doFinal(base64Decode(property)), UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private SecretKeySpec createSecretKey(String key) throws Exception {
		if (secretKey == null) {
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			// TODO get keys from vault
			byte[] keyByte = key.getBytes(UTF_8);
			keyByte = sha.digest(keyByte);
			keyByte = Arrays.copyOf(keyByte, 16);
			secretKey = new SecretKeySpec(keyByte, "AES");
		}

		return secretKey;
	}

	private String base64Encode(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private byte[] base64Decode(String property) throws Exception {
		return Base64.getDecoder().decode(property);
	}
}
