package com.syncari.core.service;

import com.syncari.core.TestConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes= TestConfig.class, loader= AnnotationConfigContextLoader.class)
public class EncryptionServiceTest {
	@Autowired
	EncryptionService service;

	@Test
	public void encryptDecrypt() throws InterruptedException {
		String encrypted = service.encrypt("test");
		assertEquals("test", service.decrypt(encrypted));
	}
	@Test
	public void decryptIfPossible() throws InterruptedException {
		//unencrypted
		Optional<String> encrypted = service.decryptIfPossible("test");
		assertTrue(encrypted.isEmpty());
		assertEquals("test", service.decryptIfPossible(service.encrypt("test")).get());
	}

	@Test
	public void doubleEncryptNotAllowed() throws InterruptedException {
		String encrypted = service.encrypt("test");
		try {
			service.encrypt(encrypted);
			fail();
		} catch (Exception e) {
			assertEquals("Value already encrypted", e.getMessage());
		}
	}
	
	@Test
	public void encryptEmptyValue() throws InterruptedException {
		try {
			service.encrypt("");
			fail();
		} catch (Exception e) {
			assertEquals("Value cannot be empty for encryption", e.getMessage());
		}
		try {
			service.encrypt(null);
			fail();
		} catch (Exception e) {
			assertEquals("Value cannot be empty for encryption", e.getMessage());
		}
	}
	
	@Test
	public void decryptEmptyValue() throws InterruptedException {
		try {
			service.decrypt("");
			fail();
		} catch (Exception e) {
			assertEquals("Value cannot be empty for decryption", e.getMessage());
		}
		try {
			service.decrypt(null);
			fail();
		} catch (Exception e) {
			assertEquals("Value cannot be empty for decryption", e.getMessage());
		}
	}
}
