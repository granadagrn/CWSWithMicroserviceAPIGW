package com.microservices.loggingservice.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/logs")
public class LoggingController {

    private static final Logger logger = LogManager.getLogger(LoggingController.class);

    /**@PostMapping
    public ResponseEntity<Void> logMessage(@RequestParam String level, @RequestParam String message) {
        switch (level.toLowerCase()) {
            case "info":
                logger.info(message);
                break;
            case "warn":
                logger.warn(message);
                break;
            case "error":
                logger.error(message);
                break;
            case "debug":
                logger.debug(message);
                break;
            default:
                logger.info("Default log: " + message);
        }
        return ResponseEntity.ok().build();
    }**/

    @PostMapping("/logs")
    public ResponseEntity<Void> receiveLog(@RequestBody String logMessage) {
        logger.info("Received log: {}", logMessage);
        return ResponseEntity.ok().build();
    }
}