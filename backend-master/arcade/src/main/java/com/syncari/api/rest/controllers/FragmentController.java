package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.FragmentTransformer;
import com.syncari.api.rest.controllers.data.fragment.FragmentDTO;
import com.syncari.core.model.Fragment;
import com.syncari.core.model.util.Scope;
import com.syncari.core.service.FragmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/fragment")
public class FragmentController {
    @Autowired
    FragmentService fragmentService;

    @Autowired
    FragmentTransformer transformer;

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline")
    public List<FragmentDTO> listEntityFragments(){
        List<Fragment> fragments = fragmentService.listEntityFragments();
        return transformer.toFragmentDTOs(fragments);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline")
    public List<FragmentDTO> listFieldFragments(){
        List<Fragment> fragments = fragmentService.listFieldFragments();
        return transformer.toFragmentDTOs(fragments);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{fragmentId}")
    public FragmentDTO getEntityFragment(@PathVariable String fragmentId){
        Fragment fragment = fragmentService.getFragment(Scope.ENTITY, fragmentId);
        return transformer.toFragmentDTO(fragment);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{fragmentId}")
    public FragmentDTO getFieldFragment(@PathVariable String fragmentId){
        Fragment fragment = fragmentService.getFragment(Scope.ATTRIBUTE, fragmentId);
        return transformer.toFragmentDTO(fragment);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline")
    public FragmentDTO createEntityFragment(@RequestBody FragmentDTO fragment){
        if(fragment.getScope() == null){
            fragment.setScope(Scope.ENTITY);
        }
        Fragment newFragment = fragmentService.createFragment(transformer.toFragment(fragment));
        return transformer.toFragmentDTO(newFragment);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/fieldPipeline")
    public FragmentDTO createFieldFragment(@RequestBody FragmentDTO fragment){
        if(fragment.getScope() == null){
            fragment.setScope(Scope.ATTRIBUTE);
        }
        Fragment newFragment = fragmentService.createFragment(transformer.toFragment(fragment));
        return transformer.toFragmentDTO(newFragment);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/entityPipeline/{fragmentId}")
    public FragmentDTO updateEntityFragment(@PathVariable String fragmentId, @RequestBody FragmentDTO fragment){
        fragment.setScope(Scope.ENTITY);
        fragment.setId(fragmentId);
        Fragment newFragment = fragmentService.updateFragment(transformer.toFragment(fragment));
        return transformer.toFragmentDTO(newFragment);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/fieldPipeline/{fragmentId}")
    public FragmentDTO updateFieldFragment(@PathVariable String fragmentId, @RequestBody FragmentDTO fragment){
        fragment.setScope(Scope.ATTRIBUTE);
        fragment.setId(fragmentId);
        Fragment newFragment = fragmentService.updateFragment(transformer.toFragment(fragment));
        return transformer.toFragmentDTO(newFragment);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/entityPipeline/{fragmentId}")
    public void deleteEntityFragment(@PathVariable String fragmentId){
        fragmentService.deleteFragment(Scope.ENTITY, fragmentId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/fieldPipeline/{fragmentId}")
    public void deleteFieldFragment(@PathVariable String fragmentId){
        fragmentService.deleteFragment(Scope.ATTRIBUTE, fragmentId);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityPipeline/{fragmentId}/share")
    public Set<String> getEntityFragmentSharedWithInstances(@PathVariable String fragmentId){
        return fragmentService.getSharingInstances(fragmentId);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldPipeline/{fragmentId}/share")
    public Set<String> getFieldFragmentSharedWithInstances(@PathVariable String fragmentId){
        return fragmentService.getSharingInstances(fragmentId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/entityPipeline/{fragmentId}/share")
    public Set<String> shareEntityFragment(@PathVariable String fragmentId, @RequestBody List<String> instances){
        Fragment fragment = fragmentService.getFragment(Scope.ENTITY, fragmentId);
        fragmentService.share(fragment.getId(), instances);
        return fragmentService.getSharingInstances(fragmentId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/fieldPipeline/{fragmentId}/share")
    public Set<String> shareFieldFragment(@PathVariable String fragmentId, @RequestBody List<String> instances){
        Fragment fragment = fragmentService.getFragment(Scope.ATTRIBUTE, fragmentId);
        fragmentService.share(fragment.getId(), instances);
        return fragmentService.getSharingInstances(fragmentId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{fragmentId}/hide")
    public FragmentDTO hideEntityFragmentShare(@PathVariable String fragmentId){
        return transformer.toFragmentDTO(fragmentService.hideFragment(Scope.ENTITY, fragmentId));
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/fieldPipeline/{fragmentId}/hide")
    public FragmentDTO hideFieldFragmentShare(@PathVariable String fragmentId){
        return transformer.toFragmentDTO(fragmentService.hideFragment(Scope.ATTRIBUTE, fragmentId));
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityPipeline/{fragmentId}/show")
    public FragmentDTO showEntityFragmentShare(@PathVariable String fragmentId){
        return transformer.toFragmentDTO(fragmentService.showFragment(Scope.ENTITY, fragmentId));
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/fieldPipeline/{fragmentId}/show")
    public FragmentDTO showFieldFragmentShare(@PathVariable String fragmentId){
        return transformer.toFragmentDTO(fragmentService.showFragment(Scope.ATTRIBUTE, fragmentId));
    }


}
