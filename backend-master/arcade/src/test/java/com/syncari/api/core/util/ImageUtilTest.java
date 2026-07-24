package com.syncari.api.core.util;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.restutils.utils.ImageUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.multipart.MultipartFile;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
public class ImageUtilTest {

    @Autowired
    private ImageUtil imageUtil;

    @Test
    public void validateFileNameandContentTest(){
        String name = "file.png";
        String originalFileName = "file.png";
        String contentType = "image/png";
        byte[] content = null;
        MultipartFile multipartFile = new MockMultipartFile(name,
                originalFileName, contentType, content);
        imageUtil.validateFile(multipartFile);
    }

    @Test(expected = SyncariValidationException.class)
    public void invalidFileextensiontest(){
        String name = "file.txt";
        String originalFileName = "file.txt";
        String contentType = "image/png";
        byte[] content = null;
        MultipartFile multipartFile = new MockMultipartFile(name,
                originalFileName, contentType, content);
        imageUtil.validateFile(multipartFile);
    }


    @Test(expected = SyncariValidationException.class)
    public void invalidFileContenttest(){
        String name = "file.txt";
        String originalFileName = "file.txt";
        String contentType = "text/plain";
        byte[] content = null;
        MultipartFile multipartFile = new MockMultipartFile(name,
                originalFileName, contentType, content);
        imageUtil.validateFile(multipartFile);
    }
}
