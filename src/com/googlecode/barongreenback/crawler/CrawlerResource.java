package com.googlecode.barongreenback.crawler;

import com.googlecode.barongreenback.jobs.JobsResource;
import com.googlecode.barongreenback.shared.Forms;
import com.googlecode.barongreenback.shared.ModelRepository;
import com.googlecode.barongreenback.shared.RecordDefinition;
import com.googlecode.barongreenback.views.Views;
import com.googlecode.funclate.Model;
import com.googlecode.totallylazy.*;
import com.googlecode.totallylazy.numbers.Numbers;
import com.googlecode.totallylazy.records.Keyword;
import com.googlecode.totallylazy.records.Record;
import com.googlecode.totallylazy.records.Records;
import com.googlecode.totallylazy.records.simpledb.mappings.Mappings;
import com.googlecode.utterlyidle.*;
import com.googlecode.utterlyidle.annotations.*;
import org.apache.lucene.queryParser.ParseException;

import java.util.UUID;

import static com.googlecode.barongreenback.crawler.CheckPointStopper.extractCheckpoint;
import static com.googlecode.barongreenback.crawler.DuplicateRemover.ignoreAlias;
import static com.googlecode.barongreenback.jobs.JobsResource.DEFAULT_INTERVAL;
import static com.googlecode.barongreenback.shared.ModelRepository.MODEL_TYPE;
import static com.googlecode.barongreenback.shared.RecordDefinition.UNIQUE_FILTER;
import static com.googlecode.barongreenback.shared.RecordDefinition.convert;
import static com.googlecode.barongreenback.views.Views.find;
import static com.googlecode.funclate.Model.model;
import static com.googlecode.totallylazy.Callables.first;
import static com.googlecode.totallylazy.Callables.toClass;
import static com.googlecode.totallylazy.Pair.pair;
import static com.googlecode.totallylazy.Predicates.is;
import static com.googlecode.totallylazy.Predicates.where;
import static com.googlecode.totallylazy.Uri.uri;
import static com.googlecode.totallylazy.proxy.Call.method;
import static com.googlecode.totallylazy.proxy.Call.on;
import static com.googlecode.totallylazy.records.Keywords.keyword;
import static com.googlecode.totallylazy.records.Using.using;
import static com.googlecode.utterlyidle.RequestBuilder.post;
import static com.googlecode.utterlyidle.annotations.AnnotatedBindings.relativeUriOf;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;

@Path("crawler")
@Produces(MediaType.TEXT_HTML)
public class CrawlerResource {
    private RequestGenerator requestGenerator;
    private final Records records;
    private final ModelRepository modelRepository;
    private final Crawler crawler;
    private final Redirector redirector;
    private final Application application;

    public CrawlerResource(final RequestGenerator requestGenerator, final Records records, final ModelRepository modelRepository, Crawler crawler, Redirector redirector, Application application) {
        this.requestGenerator = requestGenerator;
        this.records = records;
        this.modelRepository = modelRepository;
        this.crawler = crawler;
        this.redirector = redirector;
        this.application = application;
    }

    @GET
    @Path("list")
    public Model list() {
        return model().add("items", allCrawlerModels().map(asModelWithId()).toList());
    }

    @POST
    @Path("crawlAll")
    public Response crawlAll() throws Exception {
        return forAll(ids(), crawl());
    }

    @POST
    @Path("resetAll")
    public Response resetAll() throws Exception {
        return forAll(ids(), reset());
    }

    @POST
    @Path("deleteAll")
    public Response deleteAll() throws Exception {
        return forAll(ids(), delete());
    }

    private Sequence<UUID> ids() {
        return allCrawlerModels().map(first(UUID.class));
    }

    public static <T> Response forAll(final Sequence<T> sequence, final Callable1<T, Response> callable) throws Exception {
        return sequence.map(callable).last();
    }

    public Callable1<UUID, Response> crawl() {
        return new Callable1<UUID, Response>() {
            public Response call(UUID uuid) throws Exception {
                Uri crawlerUri = redirector.uriOf(method(on(CrawlerResource.class).crawl(uuid)));
                Uri jobsUri = redirector.uriOf(method(on(JobsResource.class).schedule(uuid, JobsResource.DEFAULT_INTERVAL, crawlerUri.path())));
                return application.handle(post(jobsUri).form("id", uuid).build());
            }
        };
    }

    public Callable1<UUID, Response> reset() {
        return new Callable1<UUID, Response>() {
            public Response call(UUID uuid) throws Exception {
                return application.handle(requestGenerator.requestFor(method(on(CrawlerResource.class).reset(uuid))));
            }
        };
    }

    public Callable1<UUID, Response> delete() {
        return new Callable1<UUID, Response>() {
            public Response call(UUID uuid) throws Exception {
                return application.handle(requestGenerator.requestFor(method(on(CrawlerResource.class).delete(uuid))));
            }
        };
    }

    private Sequence<Pair<UUID, Model>> allCrawlerModels() {
        return modelRepository.find(where(MODEL_TYPE, is("form")));
    }

    @GET
    @Path("export")
    @Produces("application/json")
    public String export(@QueryParam("id") UUID id) {
        return modelFor(id).toString();
    }

    @GET
    @Path("import")
    public Model importForm() {
        return model();
    }

    @POST
    @Path("import")
    public Response importJson(@FormParam("model") String model, @FormParam("id") Option<UUID> id) {
        modelRepository.set(id.getOrElse(randomUUID()), Model.parse(model));
        return redirectToCrawlerList();
    }

    @POST
    @Path("delete")
    public Response delete(@FormParam("id") UUID id) {
        modelRepository.remove(id);
        return redirectToCrawlerList();
    }

    @POST
    @Path("reset")
    public Response reset(@FormParam("id") UUID id) {
        Model model = modelRepository.get(id).get();
        Model form = model.get("form", Model.class);
        form.remove("checkpoint", String.class);
        form.add("checkpoint", "");
        form.remove("checkpointType", String.class);
        form.add("checkpointType", String.class.getName());
        modelRepository.set(id, model);
        return redirectToCrawlerList();
    }

    @GET
    @Path("new")
    public Model newForm() {
        return Forms.emptyForm(Forms.NUMBER_OF_FIELDS);
    }

    @GET
    @Path("exists")
    @Produces(MediaType.TEXT_PLAIN)
    public boolean exists(@QueryParam("id") UUID id) {
        return !modelRepository.get(id).isEmpty();
    }

    @POST
    @Path("new")
    public Response newCrawler(Model model) throws Exception {
        return edit(randomUUID(), model);
    }

    @GET
    @Path("edit")
    public Model edit(@QueryParam("id") UUID id) {
        return Forms.addTemplates(modelFor(id));
    }

    @POST
    @Path("edit")
    public Response edit(@QueryParam("id") UUID id, final Model root) throws Exception {
        Model form = root.get("form", Model.class);
        String from = form.get("from", String.class);
        String update = form.get("update", String.class);
        String more = form.get("more", String.class);
        String checkpoint = form.get("checkpoint", String.class);
        String checkpointType = form.get("checkpointType", String.class);
        Model record = form.get("record", Model.class);
        RecordDefinition recordDefinition = convert(record);
        modelRepository.set(id, Forms.form(update, from, more, checkpoint, checkpointType, recordDefinition.toModel()));
        return redirectToCrawlerList();
    }

    @POST
    @Path("crawl")
    @Produces(MediaType.TEXT_PLAIN)
    public String crawl(@FormParam("id") UUID id) throws Exception {
        Model model = modelFor(id);
        Model form = model.get("form", Model.class);
        String from = form.get("from", String.class);
        String update = form.get("update", String.class);
        String more = form.get("more", String.class);
        String checkpoint = form.get("checkpoint", String.class);
        String checkpointType = form.get("checkpointType", String.class);
        Model record = form.get("record", Model.class);
        RecordDefinition recordDefinition = convert(record);
        Sequence<Record> records = crawler.crawl(uri(from), more, convertFromString(checkpoint, checkpointType), recordDefinition);
        if (records.isEmpty()) {
            return numberOfRecordsUpdated(0);
        }
        Option<Object> firstCheckPoint = getFirstCheckPoint(records);
        modelRepository.set(id, Forms.form(update, from, more, convertToString(firstCheckPoint), getCheckPointType(firstCheckPoint), recordDefinition.toModel()));
        return put(keyword(update), recordDefinition, records);
    }

    private String getCheckPointType(Option<Object> checkpoint) {
        return checkpoint.map(toClass()).
                map(className()).
                getOrElse(String.class.getName());
    }

    private static Callable1<? super Class, String> className() {
        return new Callable1<Class, String>() {
            public String call(Class aClass) throws Exception {
                return aClass.getName();
            }
        };
    }

    private final Mappings mappings = new Mappings();

    private Object convertFromString(String checkpoint, String checkpointType) throws Exception {
        Class<?> aClass = checkpointType == null ? String.class : Class.forName(checkpointType);
        Object value = mappings.get(aClass).toValue(checkpoint);
        return value;
    }

    private String convertToString(Option<Object> checkPoint) {

        return checkPoint.map(mapAsString()).getOrElse("");
    }

    private Callable1<? super Object, String> mapAsString() {
        return new Callable1<Object, String>() {
            public String call(Object instance) throws Exception {
                return mappings.toString(instance.getClass(), instance);
            }
        };
    }

    private Option<Object> getFirstCheckPoint(Sequence<Record> records) {
        return extractCheckpoint(records.head());
    }

    private Model modelFor(UUID id) {
        return modelRepository.get(id).get();
    }

    private Callable1<? super Pair<UUID, Model>, Model> asModelWithId() {
        return new Callable1<Pair<UUID, Model>, Model>() {
            public Model call(Pair<UUID, Model> pair) throws Exception {
                return model().
                        add("id", pair.first().toString()).
                        add("model", pair.second()).
                        add("jobUrl", jobUrl(pair.first())).
                        add("resettable", hasCheckpoint(pair.second()));
            }
        };
    }

    private boolean hasCheckpoint(Model model) {
        return !Strings.isEmpty(model.get("form", Model.class).get("checkpoint", String.class));
    }

    private Uri jobUrl(UUID uuid) throws Exception {
        Uri uri = relativeUriOf(method(on(CrawlerResource.class).crawl(null)));
        return redirector.uriOf(method(on(JobsResource.class).schedule(uuid, DEFAULT_INTERVAL, "/" + uri.toString())));
    }

    private Response redirectToCrawlerList() {
        return redirector.seeOther(method(on(getClass()).list()));
    }

    private Response redirectToJobsList() {
        return redirector.seeOther(method(on(JobsResource.class).list()));
    }

    private String put(final Keyword<Object> recordName, RecordDefinition recordDefinition, final Sequence<Record> recordsToAdd) throws ParseException {
        Sequence<Keyword> keywords = RecordDefinition.allFields(recordDefinition).map(ignoreAlias());
        if (find(modelRepository, recordName.name()).isEmpty()) {
            modelRepository.set(randomUUID(), Views.convertToViewModel(recordName, keywords));
        }
        records.define(recordName, keywords.toArray(Keyword.class));
        Number updated = 0;
        for (Record record : recordsToAdd) {
            Sequence<Keyword> unique = record.keywords().filter(UNIQUE_FILTER);
            Number rows = records.put(recordName, pair(using(unique).call(record), record));
            updated = Numbers.add(updated, rows);
        }
        return numberOfRecordsUpdated(updated);
    }

    private String numberOfRecordsUpdated(Number updated) {
        return format("%s Records updated", updated);
    }
}
