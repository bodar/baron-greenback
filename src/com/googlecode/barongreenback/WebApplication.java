package com.googlecode.barongreenback;

import com.googlecode.utterlyidle.RestApplication;
import com.googlecode.utterlyidle.ServerConfiguration;
import com.googlecode.utterlyidle.httpserver.RestServer;

public class WebApplication extends RestApplication {
    public static void main(String[] args) throws Exception {
        new RestServer(new WebApplication(), ServerConfiguration.defaultConfiguration());
    }
}
