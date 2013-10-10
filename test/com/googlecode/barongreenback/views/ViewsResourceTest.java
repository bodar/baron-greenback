package com.googlecode.barongreenback.views;

import com.googlecode.barongreenback.search.SearchResource;
import com.googlecode.barongreenback.shared.ApplicationTests;
import com.googlecode.barongreenback.shared.ModelRepository;
import com.googlecode.totallylazy.Block;
import com.googlecode.totallylazy.matchers.NumberMatcher;
import com.googlecode.yadic.Container;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static com.googlecode.funclate.Model.mutable.model;
import static com.googlecode.totallylazy.Arrays.list;
import static com.googlecode.totallylazy.numbers.Numbers.subtract;
import static com.googlecode.totallylazy.proxy.Call.method;
import static com.googlecode.totallylazy.proxy.Call.on;
import static com.googlecode.utterlyidle.annotations.AnnotatedBindings.relativeUriOf;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

public class ViewsResourceTest extends ApplicationTests {
    private static final UUID VIEW_ID = randomUUID();

    @Before
    public void createView() {
        application.usingRequestScope(new Block<Container>() {
            public void execute(Container container) throws Exception {
                ModelRepository repository = container.get(ModelRepository.class);
                repository.set(VIEW_ID, model().
                        add("view", model().
                                add("name", "users").
                                add("records", "users").
                                add("query", "").
                                add("visible", true).
                                add("keywords", list(model().
                                        add("name", "firstname").
                                        add("alias", "").
                                        add("group", "").
                                        add("type", "java.lang.String").
                                        add("visible", true).
                                        add("unique", false)))));
                repository.set(UUID.randomUUID(), model().
                        add("view", model().
                                add("name", "news").
                                add("records", "news").
                                add("query", "").
                                add("visible", true).
                                add("keywords", list(model().
                                        add("name", "title").
                                        add("alias", "").
                                        add("group", "").
                                        add("type", "java.lang.String").
                                        add("visible", true).
                                        add("unique", false)))));
                repository.set(UUID.randomUUID(), model().
                        add("view", model().
                                add("name", "hidden view").
                                add("records", "news").
                                add("query", "").
                                add("visible", false).
                                add("keywords", list(model().
                                        add("name", "title").
                                        add("alias", "").
                                        add("group", "").
                                        add("type", "java.lang.String").
                                        add("visible", true).
                                        add("unique", false)))));
            }
        });
    }

    @Test
    public void menuOnlyDisplaysVisibleViews() throws Exception {
        MenuPage menu = new MenuPage(browser);
        assertThat(menu.numberOfItems(), NumberMatcher.is(2));
        assertThat(menu.link("users").value(), is("/" + relativeUriOf(method(on(SearchResource.class).list("users", "")))));
        assertThat(menu.link("news").value(), is("/" + relativeUriOf(method(on(SearchResource.class).list("news", "")))));
    }

    @Test
    public void canEdit() throws Exception {
        ViewListPage views = new ViewListPage(browser);
        assertThat(relativeUriOf(method(on(ViewsResource.class).edit(VIEW_ID))).toString(), endsWith(views.link("users").value()));
        ViewEditPage edit = views.edit(VIEW_ID);
        assertThat(edit.name().value(), is("users"));
        assertThat(edit.records().value(), is("users"));
        assertThat(edit.query().value(), is(""));
        assertThat(edit.fieldName(1).value(), is("firstname"));
        edit.name().value("people");
        edit.records().value("people");
        edit.query().value("firstname:dan");

        ViewListPage modifiedViews = edit.save();

        assertThat(relativeUriOf(method(on(ViewsResource.class).edit(VIEW_ID))).toString(), endsWith(modifiedViews.link("people").value()));
        ViewEditPage modifiedView = views.edit(VIEW_ID);
        assertThat(modifiedView.records().value(), is("people"));
        assertThat(modifiedView.query().value(), is("firstname:dan"));
        assertThat(modifiedView.fieldName(1).value(), is("firstname"));
    }

    @Test
    public void deleteView() throws Exception {
        ViewListPage views = new ViewListPage(browser);
        Number numberOfViews = views.count();
        ViewListPage newPage = views.delete(VIEW_ID);
        assertThat(newPage.count(), is(subtract(numberOfViews, 1)));
    }
}