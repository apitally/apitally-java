package com.apitally.spring.app;

import com.apitally.common.dto.Request;
import com.apitally.common.dto.Response;

public class RequestLoggingCallbacks implements com.apitally.common.RequestLoggingCallbacks {

    @Override
    public boolean shouldExclude(Request request, Response response) {
        return true;
    }
}
