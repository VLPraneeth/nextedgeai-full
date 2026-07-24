package com.syncari.api.ui.controllers;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.DebugConfig;
import com.syncari.core.service.InstanceConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

import static com.syncari.api.ui.util.HTMXUtils.htmx;
import static com.syncari.core.security.Permissions.EDIT_DEBUG_MODE;
import static com.syncari.core.security.Permissions.READ_DEBUG_MODE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Slf4j
@Controller
public class DebugController {

    @Autowired
    private InstanceConfigurationService instanceConfigurationService;

    @Secured(READ_DEBUG_MODE)
    @RequestMapping(path = "/ui/debug", method = GET)
    public String getDebug(HttpServletRequest request, Model model) {
        if (!isSuperAdminOrGhost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        DebugConfig debugConfig = instanceConfigurationService.getDebugConfig();
        model.addAttribute("debugEnabled", debugConfig.isEnabled());
        model.addAttribute("remainingSeconds", debugConfig.getRemainingSeconds());
        model.addAttribute("now", LocalDateTime.now().toString());
        return htmx("debug", request);
    }

    @Secured(EDIT_DEBUG_MODE)
    @RequestMapping(path = "/ui/debug", method = POST)
    public String postDebug(
            @RequestParam(value = "debugMode", required = false) Boolean debugMode,
            @RequestParam(value = "expiryMinutes", defaultValue = "15") int expiryMinutes,
            HttpServletRequest request,
            Model model) {

        if (!isSuperAdminOrGhost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        try {
            int expirySeconds = expiryMinutes * 60;
            boolean enabled = debugMode != null && debugMode;

            DebugConfig debugConfig = DebugConfig.builder()
                    .enabled(enabled)
                    .expirySeconds(expirySeconds)
                    .build();

            instanceConfigurationService.updateDebugConfig(debugConfig);

            if (enabled) {
                log.info("Debug mode enabled for {} minutes", expiryMinutes);
                model.addAttribute("message", "Debug mode enabled for " + expiryMinutes + " minutes");
            } else {
                log.info("Debug mode disabled");
                model.addAttribute("message", "Debug mode disabled");
            }
            model.addAttribute("messageType", "success");
            model.addAttribute("debugEnabled", enabled);

        } catch (Exception e) {
            log.error("Error updating debug mode", e);
            model.addAttribute("message", "Error: " + e.getMessage());
            model.addAttribute("messageType", "error");
            DebugConfig currentConfig = instanceConfigurationService.getDebugConfig();
            model.addAttribute("debugEnabled", currentConfig.isEnabled());
            model.addAttribute("remainingSeconds", currentConfig.getRemainingSeconds());
            model.addAttribute("now", LocalDateTime.now().toString());
            return htmx("debug", request);
        }

        DebugConfig updatedConfig = instanceConfigurationService.getDebugConfig();
        model.addAttribute("remainingSeconds", updatedConfig.getRemainingSeconds());
        model.addAttribute("now", LocalDateTime.now().toString());
        return htmx("debug", request);
    }

    private boolean isSuperAdminOrGhost() {
        return SyncariContext.getUser().isSuperAdmin() || SyncariContext.isGhost();
    }
}

