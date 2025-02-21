package com.apitally.spring.app;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.apitally.spring.ApitallyConsumer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@Validated
public class TestController {
    @GetMapping("/items")
    public List<TestItem> getItems(HttpServletRequest request) {
        ApitallyConsumer consumer = new ApitallyConsumer("tester", "Tester", "Test Group");
        request.setAttribute("apitallyConsumer", consumer);
        List<TestItem> items = new ArrayList<TestItem>();
        items.add(new TestItem(1, "bob"));
        items.add(new TestItem(2, "alice"));
        return items;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public void addItem(@Valid @RequestBody TestItem newItem) {
    }

    @GetMapping("/items/{id}")
    public TestItem getItem(@PathVariable @Min(1) Integer id) {
        TestItem item = new TestItem(id, "bob");
        return item;
    }

    @PutMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateItem(@Valid @RequestBody TestItem newItem, @PathVariable @Min(1) Integer id) {
    }

    @DeleteMapping(value = "/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable @Min(1) Integer id) {
    }

    @GetMapping(value = "/healthz", produces = "application/json; charset=utf-8")
    public String getHealthCheck() {
        return "{ \"healthy\" : true }";
    }

    @GetMapping(value = "/throw", produces = "application/json; charset=utf-8")
    public String getError() {
        throw new TestException("test");
    }
}
