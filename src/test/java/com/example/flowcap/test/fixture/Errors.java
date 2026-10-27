package com.example.flowcap.test.fixture;

import com.example.flowcap.FlowStep;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class Errors {

    @ExceptionHandler(IllegalStateException.class)
    @FlowStep(action = "Map failure", participant = Ids.APP)
    public String failed(IllegalStateException e) {
        return "failed";
    }
}
