package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.BrandResponse;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.BrandDetail;
import com.syncari.core.model.LogoType;
import com.syncari.core.model.Organization;
import com.syncari.core.service.BrandService;
import com.syncari.restutils.utils.ImageUtil;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.core.utils.ImageUtils.getMediaType;

@Slf4j
@RestController
@RequestMapping(value = "api/v1/brand")
public class BrandController {

    @Autowired
    BrandService brandService;
    @Autowired
    ImageUtil imageUtil;
    @Autowired
    ObjectTransformer objectTransformer;

    @Secured(READ_BRAND)
    @RequestMapping(method = RequestMethod.GET)
    public BrandResponse getBrandDetails(){
        BrandDetail brandDetail = brandService.getBrandDetails(SyncariContext.getOrganziation().getId());
        return objectTransformer.toBrandResponse(brandDetail);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/logo")
    @ResponseBody
    public ResponseEntity<StreamingResponseBody> getBrandLogo() {
        return streamImage(() -> brandService.getLogo(SyncariContext.getOrganziation().getId(), LogoType.REGULAR));
    }

    @RequestMapping(method = RequestMethod.GET, value = "/logoSquare")
    @ResponseBody
    public ResponseEntity<StreamingResponseBody> getBrandLogoSquare() {
        return streamImage(() -> brandService.getImageSquare(SyncariContext.getOrganziation().getId()));
    }

    private ResponseEntity<StreamingResponseBody> streamImage(Supplier<Pair<String, InputStream>> logoSource) {
        Pair<String, InputStream> photoStream = logoSource.get();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(getMediaType(photoStream.getX()));
        StreamingResponseBody stream = outputStream -> photoStream.getY().transferTo(outputStream);
        return new ResponseEntity<>(stream, headers, HttpStatus.OK);
    }

    @Secured(WRITE_BRAND)
    @RequestMapping(method = RequestMethod.PUT)
    public BrandResponse updateBrand(@RequestParam("name") String name,
                                     @RequestParam(name = "logo", required = false) MultipartFile image,
                                     @RequestParam(name = "logoSquare", required = false) MultipartFile imageSquare,
                                     @RequestParam("color") String color) throws IOException {
        if(Objects.nonNull(image) && !image.isEmpty())
            imageUtil.validateFile(image);
        if(Objects.nonNull(imageSquare) && !imageSquare.isEmpty())
            imageUtil.validateFile(imageSquare);
        Organization org = SyncariContext.getOrganziation();

        BrandDetail brandDetail = brandService.updateBrand(org.getId(), image, imageSquare, name, color);
        return objectTransformer.toBrandResponse(brandDetail);
    }

    @Secured(WRITE_BRAND)
    @RequestMapping(method = RequestMethod.DELETE, value = "/reset")
    public BrandResponse reset() {
        brandService.reset(SyncariContext.getOrganziation().getId());
        BrandDetail brandDetail = brandService.getBrandDetails(SyncariContext.getOrganziation().getId());
        return objectTransformer.toBrandResponse(brandDetail);
    }
}


