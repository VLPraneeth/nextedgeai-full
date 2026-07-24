package com.syncari.core.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.core.Features;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.BrandDetail;
import com.syncari.core.model.LogoType;
import com.syncari.core.repositories.syncari.BrandDetailRepo;
import com.syncari.utils.Pair;
import com.syncari.utils.file.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BrandService {

    @Autowired
    GCSFileManager gcsFileManager;
    @Autowired
    BrandDetailRepo brandDetailRepo;
    @Autowired
    FileUtil fileUtil;
    @Autowired
    FeatureService featureService;
    @Autowired
    ConnectorService connectorService;

    public static final String BRAND_DEFAULT_COLOR = "#578CEB";
    public static final String SYNCARI = "Syncari";
    public static final String DEFAULT_BRAND_ICON = "syncari_default_logo.png";
    public static final String DEFAULT_BRAND_SQUARE_ICON = "syncari_default_logo_square.png";

    private final static String BRAND_FILE_NAME_PATTERN = "%s/brandImage_%s";
    private final static String BRAND_SQUARE_FILE_NAME_PATTERN = "%s/brandSquareImage_%s";
    public final static String BRAND_DEFAULT_ICON_URI = "/arcade/api/v1/brand/logoSquare";

//    LoadingCache<String, BrandDetail> brandCache = CacheBuilder.newBuilder().maximumSize(100000)
//            .expireAfterAccess(15, TimeUnit.MINUTES)
//            .build(new CacheLoader<>() {
//                @Override
//                public BrandDetail load(String orgId) {
//                    return fetchBrandDetails(orgId);
//                }
//            });

//    LoadingCache<String, Pair<String, InputStream>> squareImageCache = CacheBuilder.newBuilder().maximumSize(100000)
//            .expireAfterAccess(15, TimeUnit.MINUTES)
//            .build(new CacheLoader<>() {
//                @Override
//                public Pair<String, InputStream> load(String orgId) {
//                    return getLogo(orgId, LogoType.SQUARE);
//                }
//            });

    public BrandDetail getBrandDetails(String orgId){
        return fetchBrandDetails(orgId);//brandCache.getUnchecked(orgId);
    }

    public Pair<String, InputStream> getImageSquare(String orgId){
        Pair<String, InputStream> unchecked = getLogo(orgId, LogoType.SQUARE);//squareImageCache.getUnchecked(orgId);
        try {
            unchecked.getY().reset();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load brand icons", e);
        }
        return unchecked;
    }

    private BrandDetail fetchBrandDetails(String orgId){
        BrandDetail brandDetail = brandDetailRepo.findByOrgId(orgId).orElse(null);
        if(Objects.isNull(brandDetail)) {
            BrandDetail newBrandDetail = new BrandDetail().setOrgId(orgId)
                    .setLogoLocation(DEFAULT_BRAND_ICON).setLogoSquareLocation(DEFAULT_BRAND_SQUARE_ICON)
                    .setName(SYNCARI).setColor(BRAND_DEFAULT_COLOR);
           return brandDetailRepo.save(newBrandDetail);
        }
        return brandDetail;
    }

    public BrandDetail updateBrand(String orgId, MultipartFile iconStream, MultipartFile iconSquareStream,
                                   String name, String color) throws IOException {
        BrandDetail existingBrandDetail = fetchBrandDetails(orgId);//brandCache.getUnchecked(orgId);

        if (Objects.nonNull(iconStream) && !iconStream.isEmpty()) {
            String iconUri = String.format(BRAND_FILE_NAME_PATTERN, orgId, fileUtil.sanitizeFileName(iconStream.getOriginalFilename()));
            gcsFileManager.uploadFile(iconStream.getInputStream(), iconUri);
            existingBrandDetail.setLogoLocation(iconUri);
        }
        if (Objects.nonNull(iconSquareStream) && !iconSquareStream.isEmpty()) {
            String iconSquareUri = String.format(BRAND_SQUARE_FILE_NAME_PATTERN, orgId, fileUtil.sanitizeFileName(iconSquareStream.getOriginalFilename()));
            gcsFileManager.uploadFile(iconSquareStream.getInputStream(), iconSquareUri);
            existingBrandDetail.setLogoSquareLocation(iconSquareUri);
        }

        existingBrandDetail.setColor(!StringUtils.isBlank(color) ? color : BRAND_DEFAULT_COLOR);
        existingBrandDetail.setName(!StringUtils.isBlank(name) ? name : SYNCARI);
        brandDetailRepo.save(existingBrandDetail);
//        brandCache.invalidate(orgId);
//        squareImageCache.invalidate(orgId);
        connectorService.invalidateSyncariConnectorCache();
        return fetchBrandDetails(orgId);//brandCache.getUnchecked(orgId);
    }

    public Pair<String, InputStream> getLogo(String orgId, LogoType type) {
        BrandDetail brandDetail = fetchBrandDetails(orgId);//brandCache.getUnchecked(orgId);
        String logoLocation;
        if (type == LogoType.SQUARE) {
            logoLocation = brandDetail.getLogoSquareLocation();
        } else {
            logoLocation = brandDetail.getLogoLocation();
            return Pair.of(logoLocation, gcsFileManager.readFile(logoLocation));
        }

        InputStream inputStream;
        try {
            inputStream = createReusableImageStream(gcsFileManager.readFile(logoLocation));
        } catch (IOException e) {
            log.error("Failed to load and cache input stream for brand icons{}", e.getLocalizedMessage());
            throw new RuntimeException("Failed to load brand icons", e);
        }
        return Pair.of(logoLocation, inputStream);
    }

    public InputStream createReusableImageStream(InputStream inputStream) throws IOException {
        byte[] imageData = inputStream.readAllBytes();
        return new ByteArrayInputStream(imageData);
    }

    public void reset(String orgId) {
        brandDetailRepo.deleteByOrgId(orgId);
//        brandCache.invalidate(orgId);
//        squareImageCache.invalidate(orgId);
    }

    public boolean isEnabled(){
        return featureService.isEnabled(Features.BRAND, true);
    }
}
