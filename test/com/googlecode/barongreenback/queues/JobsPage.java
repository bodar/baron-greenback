package com.googlecode.barongreenback.queues;

import com.googlecode.totallylazy.Uri;
import com.googlecode.utterlyidle.HttpHandler;
import com.googlecode.utterlyidle.Request;
import com.googlecode.utterlyidle.Response;
import com.googlecode.utterlyidle.Status;
import com.googlecode.utterlyidle.html.Html;
import com.googlecode.utterlyidle.jobs.JobsResource;

import java.util.UUID;

import static com.googlecode.totallylazy.matchers.Matchers.is;
import static com.googlecode.totallylazy.proxy.Call.method;
import static com.googlecode.totallylazy.proxy.Call.on;
import static com.googlecode.utterlyidle.RequestBuilder.get;
import static com.googlecode.utterlyidle.RequestBuilder.modify;
import static com.googlecode.utterlyidle.annotations.AnnotatedBindings.relativeUriOf;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class JobsPage {
    private final HttpHandler httpHandler;
    private final Html html;

    public JobsPage(HttpHandler httpHandler) throws Exception {
        this(httpHandler, httpHandler.handle(get("/" + relativeUriOf(method(on(JobsResource.class).list()))).build()));
    }

    public JobsPage(HttpHandler httpHandler, Response response) throws Exception {
        this.httpHandler = httpHandler;
        assertThat(response.status(), is(Status.OK));
        this.html = Html.html(response);
        assertThat(html.title(), containsString("Jobs"));
    }

    public int numberOfCompletedJobs() {
        return html.count("//tr[@class='completed']").intValue();
    }

    public Response queue(Request request) throws Exception {
        Uri resource = request.uri();
        String queuedPath = "/" + relativeUriOf(method(on(JobsResource.class).create(request, "/" + resource.path()))).toString();
        return httpHandler.handle(modify(request).uri(resource.path(queuedPath)).build());
    }

    public JobsPage deleteAll() throws Exception {
        Request request = html.form("//form[@class='deleteAll']").submit("descendant::input[@class='deleteAll']");
        return new JobsPage(httpHandler, httpHandler.handle(request));
    }

    public int responseStatusFor(UUID crawlerId) {
        return Integer.valueOf(html.selectContent(format("//*[@class='completed' and descendant::*[@class='entity' and contains(text(), '%s')]]/descendant::*[@class='response']/*[@class='code']/text()", crawlerId)));
    }

    @Override
    public String toString() {
        return html.toString();
    }
}