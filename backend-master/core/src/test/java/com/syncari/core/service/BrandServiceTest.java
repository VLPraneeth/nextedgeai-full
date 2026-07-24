package com.syncari.core.service;

import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.BrandDetail;
import com.syncari.core.model.LogoType;
import com.syncari.core.repositories.syncari.BrandDetailRepo;
import com.syncari.utils.Pair;
import com.syncari.utils.file.FileUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BrandServiceTest {

    @Mock
    private BrandDetailRepo brandDetailRepo;

    @Mock
    private GCSFileManager gcsFileManager;

    @Mock
    private ConnectorService connectorService;

    @Mock
    private FileUtil fileUtil;

    @Mock
    private FeatureService featureService;

    private BrandService brandService;

    private static final String TEST_ORG_ID = "test-org-123";
    private static final String DEFAULT_BRAND_ICON = "default-icon.png";
    private static final String DEFAULT_BRAND_SQUARE_ICON = "default-square-icon.png";
    private static final String SYNCARI = "Syncari";
    private static final String BRAND_DEFAULT_COLOR = "#000000";

    private BrandDetail testBrandDetail;

    @Before
    public void setUp() {
        brandService = new BrandService();
        brandService.brandDetailRepo = brandDetailRepo;
        brandService.fileUtil = fileUtil;
        brandService.gcsFileManager = gcsFileManager;
        brandService.featureService = featureService;
        brandService.connectorService = connectorService;

        testBrandDetail = new BrandDetail();
        testBrandDetail.setOrgId(TEST_ORG_ID);
        testBrandDetail.setLogoLocation(DEFAULT_BRAND_ICON);
        testBrandDetail.setLogoSquareLocation(DEFAULT_BRAND_SQUARE_ICON);
        testBrandDetail.setName(SYNCARI);
        testBrandDetail.setColor(BRAND_DEFAULT_COLOR);
    }

    // Test fetchBrandDetails - existing brand
    @Test
    public void testFetchBrandDetails_WhenBrandExists_ShouldReturnExistingBrand() {
        when(brandDetailRepo.findByOrgId(TEST_ORG_ID)).thenReturn(Optional.of(testBrandDetail));

        BrandDetail result = brandService.getBrandDetails(TEST_ORG_ID);

        assertNotNull(result);
        assertEquals(TEST_ORG_ID, result.getOrgId());
        verify(brandDetailRepo).findByOrgId(TEST_ORG_ID);
        verify(brandDetailRepo, never()).save(any(BrandDetail.class));
    }

    // Test getBrandDetails
    @Test
    public void testGetBrandDetails_ShouldReturnBrandFromCache() {
        when(brandDetailRepo.findByOrgId(TEST_ORG_ID)).thenReturn(Optional.of(testBrandDetail));

        BrandDetail result = brandService.getBrandDetails(TEST_ORG_ID);

        assertNotNull(result);
        assertEquals(TEST_ORG_ID, result.getOrgId());
    }

    // Test updateBrand - with icons
    @Test
    public void testUpdateBrand_WithBothIcons_ShouldUploadAndUpdate() throws Exception {
        MultipartFile iconStream = mock(MultipartFile.class);
        MultipartFile iconSquareStream = mock(MultipartFile.class);

        when(iconStream.getOriginalFilename()).thenReturn("logo.png");
        when(iconSquareStream.getOriginalFilename()).thenReturn("logo-square.png");
        when(iconStream.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(iconSquareStream.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(fileUtil.sanitizeFileName("logo.png")).thenReturn("logo.png");
        when(fileUtil.sanitizeFileName("logo-square.png")).thenReturn("logo-square.png");
        when(brandDetailRepo.findByOrgId(TEST_ORG_ID)).thenReturn(Optional.of(testBrandDetail));
        when(brandDetailRepo.save(any(BrandDetail.class))).thenReturn(testBrandDetail);

        BrandDetail result = brandService.updateBrand(TEST_ORG_ID,
                iconStream, iconSquareStream, "New Name", "#FFFFFF");

        assertNotNull(result);
        verify(gcsFileManager, times(2)).uploadFile(any(InputStream.class), anyString());
        verify(connectorService, times(1)).invalidateSyncariConnectorCache();
        verify(brandDetailRepo).save(any(BrandDetail.class));
    }

    // Test updateBrand - without icons
    @Test
    public void testUpdateBrand_WithoutIcons_ShouldUseDefaults() throws Exception {
        when(brandDetailRepo.findByOrgId(TEST_ORG_ID)).thenReturn(Optional.of(testBrandDetail));
        when(brandDetailRepo.save(any(BrandDetail.class))).thenReturn(testBrandDetail);

        BrandDetail result = brandService.updateBrand(TEST_ORG_ID,
                null, null, "New Name", "#FFFFFF");

        assertNotNull(result);
        verify(gcsFileManager, never()).uploadFile(any(InputStream.class), anyString());
        verify(brandDetailRepo).save(any(BrandDetail.class));
    }

    // Test updateBrand - with blank name and color
    @Test
    public void testUpdateBrand_WithBlankNameAndColor_ShouldUseDefaults() throws Exception {
        when(brandDetailRepo.findByOrgId(TEST_ORG_ID)).thenReturn(Optional.of(testBrandDetail));
        when(brandDetailRepo.save(any(BrandDetail.class))).thenReturn(testBrandDetail);

        brandService.updateBrand(TEST_ORG_ID, null, null, "", "");

        verify(brandDetailRepo).save(any(BrandDetail.class));
    }

    // Test getLogo - regular type
    @Test
    public void testGetLogo_RegularType_ShouldReturnRegularLogo() {
        InputStream mockStream = new ByteArrayInputStream(new byte[0]);
        when(brandDetailRepo.findByOrgId(TEST_ORG_ID)).thenReturn(Optional.of(testBrandDetail));
        when(gcsFileManager.readFile(DEFAULT_BRAND_ICON)).thenReturn(mockStream);

        Pair<String, InputStream> result = brandService.getLogo(TEST_ORG_ID, LogoType.REGULAR);

        assertNotNull(result);
        assertEquals(DEFAULT_BRAND_ICON, result.getX());
        assertNotNull(result.getY());
        verify(gcsFileManager).readFile(DEFAULT_BRAND_ICON);
    }

    // Test getLogo - square type
    @Test
    public void testGetLogo_SquareType_ShouldReturnSquareLogo() {
        InputStream mockStream = new ByteArrayInputStream(new byte[0]);
        when(brandDetailRepo.findByOrgId(TEST_ORG_ID)).thenReturn(Optional.of(testBrandDetail));
        when(gcsFileManager.readFile(DEFAULT_BRAND_SQUARE_ICON)).thenReturn(mockStream);

        Pair<String, InputStream> result = brandService.getLogo(TEST_ORG_ID, LogoType.SQUARE);

        assertNotNull(result);
        assertEquals(DEFAULT_BRAND_SQUARE_ICON, result.getX());
        assertNotNull(result.getY());
        verify(gcsFileManager).readFile(DEFAULT_BRAND_SQUARE_ICON);
    }

    // Test reset
    @Test
    public void testReset_ShouldDeleteBrandAndInvalidateCache() {
        doNothing().when(brandDetailRepo).deleteByOrgId(TEST_ORG_ID);

        brandService.reset(TEST_ORG_ID);

        verify(brandDetailRepo).deleteByOrgId(TEST_ORG_ID);
    }

    // Test isEnabled - true
    @Test
    public void testIsEnabled_WhenFeatureEnabled_ShouldReturnTrue() {
        when(featureService.isEnabled(any(), anyBoolean())).thenReturn(true);

        boolean result = brandService.isEnabled();

        assertTrue(result);
        verify(featureService).isEnabled(any(), anyBoolean());
    }

    // Test isEnabled - false
    @Test
    public void testIsEnabled_WhenFeatureDisabled_ShouldReturnFalse() {
        when(featureService.isEnabled(any(), anyBoolean())).thenReturn(false);

        boolean result = brandService.isEnabled();

        assertFalse(result);
        verify(featureService).isEnabled(any(), anyBoolean());
    }

}
