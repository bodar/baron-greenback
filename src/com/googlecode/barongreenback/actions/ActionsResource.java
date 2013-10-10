package com.googlecode.barongreenback.actions;

import com.googlecode.barongreenback.search.RecordsService;
import com.googlecode.barongreenback.search.SearchResource;
import com.googlecode.barongreenback.shared.AdvancedMode;
import com.googlecode.funclate.Model;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.Sequences;
import com.googlecode.totallylazy.Strings;
import com.googlecode.totallylazy.Uri;
import com.googlecode.utterlyidle.MediaType;
import com.googlecode.utterlyidle.Redirector;
import com.googlecode.utterlyidle.Response;
import com.googlecode.utterlyidle.annotations.DefaultValue;
import com.googlecode.utterlyidle.annotations.FormParam;
import com.googlecode.utterlyidle.annotations.GET;
import com.googlecode.utterlyidle.annotations.POST;
import com.googlecode.utterlyidle.annotations.Path;
import com.googlecode.utterlyidle.annotations.PathParam;
import com.googlecode.utterlyidle.annotations.Produces;
import com.googlecode.utterlyidle.annotations.QueryParam;

import static com.googlecode.barongreenback.views.ViewsRepository.unwrap;
import static com.googlecode.funclate.Model.mutable.model;
import static com.googlecode.totallylazy.Sequences.sequence;
import static com.googlecode.totallylazy.proxy.Call.method;
import static com.googlecode.totallylazy.proxy.Call.on;

@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
@Path("{view}/actions")
public class ActionsResource {
    private final AdvancedMode mode;
    private final Redirector redirector;
    private final RecordsService recordsService;

    public ActionsResource(AdvancedMode mode, Redirector redirector, RecordsService recordsService) {
        this.mode = mode;
        this.redirector = redirector;
        this.recordsService = recordsService;
    }

    @GET
    @Path("list")
    public Model get(@PathParam("view") String viewName, @QueryParam("query") String query) {
        Sequence<Uri> normalActions = Sequences.sequence(redirector.uriOf(method(on(ActionsResource.class).exportModel(viewName, query))));
        Sequence<Uri> advancedActions = Sequences.sequence(redirector.uriOf(method(on(ActionsResource.class).deleteModel(viewName, query))));

        Sequence<Uri> actions = (AdvancedMode.Enable.equals(mode)) ? normalActions.join(advancedActions) : normalActions;

        return model().add("actions", actions.toList());
    }

    private Model action(final Uri uri, final String name, final String method) {
        return model().
                add("name", name).
                add("url", uri).
                add("method", method);
    }

    @GET
    @Path("export")
    public Model exportModel(@PathParam("view") String viewName, @QueryParam("query") String query) {
        return action(redirector.uriOf(method(on(SearchResource.class).exportCsv(viewName, query))), "export", "GET");
    }

    @GET
    @Path("delete")
    public Model deleteModel(@PathParam("view") String viewName, @QueryParam("query") String query) {
        return action(redirector.uriOf(method(on(ActionsResource.class).delete(viewName, query, Sequences.<String>empty()))), "delete", "POST");
    }


    @POST
    @Path("delete")
    public Response delete(@PathParam("view") String viewName, @FormParam("query") @DefaultValue("") String query) {
        recordsService.delete(viewName, query);
        return redirector.seeOther(method(on(SearchResource.class).list(viewName, query)));
    }


    @POST
    @Path("delete")
    public Response delete(@PathParam("view") String viewName, @QueryParam("query") String query, @FormParam("id") Iterable<String> id) {
        String idName = unwrap(recordsService.view(viewName)).get("keywords", Model.class).get("name", String.class);
        delete(viewName, sequence(id).map(Strings.replace("\"", "\\\"")).map(Strings.format(idName + ":\"%s\"")).toString(" OR "));
        return redirector.seeOther(method(on(SearchResource.class).list(viewName, query)));
    }
}