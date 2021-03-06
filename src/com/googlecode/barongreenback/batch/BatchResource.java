package com.googlecode.barongreenback.batch;

import com.googlecode.barongreenback.crawler.executor.CrawlerExecutors;
import com.googlecode.barongreenback.shared.ModelCache;
import com.googlecode.barongreenback.shared.ModelRepository;
import com.googlecode.barongreenback.shared.messages.Category;
import com.googlecode.barongreenback.shared.messages.Messages;
import com.googlecode.funclate.Model;
import com.googlecode.funclate.json.Json;
import com.googlecode.lazyrecords.Record;
import com.googlecode.lazyrecords.lucene.Persistence;
import com.googlecode.totallylazy.Callable1;
import com.googlecode.totallylazy.Callable2;
import com.googlecode.totallylazy.Files;
import com.googlecode.totallylazy.Pair;
import com.googlecode.totallylazy.Predicates;
import com.googlecode.totallylazy.time.Clock;
import com.googlecode.utterlyidle.MediaType;
import com.googlecode.utterlyidle.Redirector;
import com.googlecode.utterlyidle.Response;
import com.googlecode.utterlyidle.StreamingOutput;
import com.googlecode.utterlyidle.annotations.FormParam;
import com.googlecode.utterlyidle.annotations.GET;
import com.googlecode.utterlyidle.annotations.POST;
import com.googlecode.utterlyidle.annotations.Path;
import com.googlecode.utterlyidle.annotations.Produces;
import com.googlecode.utterlyidle.annotations.QueryParam;
import com.googlecode.utterlyidle.jobs.Jobs;
import com.googlecode.utterlyidle.schedules.HttpScheduler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.googlecode.barongreenback.shared.messages.Messages.error;
import static com.googlecode.barongreenback.shared.messages.Messages.success;
import static com.googlecode.funclate.Model.mutable.model;
import static com.googlecode.totallylazy.Files.hasSuffix;
import static com.googlecode.totallylazy.Files.recursiveFiles;
import static com.googlecode.totallylazy.Streams.copy;
import static com.googlecode.totallylazy.Zip.zip;
import static com.googlecode.totallylazy.proxy.Call.method;
import static com.googlecode.totallylazy.proxy.Call.on;
import static com.googlecode.totallylazy.time.Dates.LEXICAL;
import static com.googlecode.utterlyidle.ResponseBuilder.response;
import static java.lang.String.format;

@Path("batch")
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
public class BatchResource {
    public static final UUID BACKUP_JOB_ID = UUID.fromString("70203355-d7d3-4477-85ef-d3309f21fae0");
    private final ModelRepository modelRepository;
    private final Redirector redirector;
    private final Persistence persistence;
    private final HttpScheduler scheduler;
    private final Jobs jobs;
    private final ModelCache cache;
    private final Clock clock;
    private final CrawlerExecutors crawlerExecutors;
    private final FileRoller fileRoller;
    private final BackupStart backupStart;
    private final BackupInterval backupInterval;
    private final BackupsLocation backupsLocation;
    private AutoBackupsLocation autoBackupsLocation;

    public BatchResource(final ModelRepository modelRepository, final Redirector redirector, final Persistence persistence, final HttpScheduler scheduler,
                         final Jobs jobs, final ModelCache cache, final Clock clock, final CrawlerExecutors crawlerExecutors, final FileRoller fileRoller,
                         final BackupStart backupStart, final BackupInterval backupInterval, final BackupsLocation backupsLocation, final AutoBackupsLocation autoBackupsLocation) {
        this.modelRepository = modelRepository;
        this.redirector = redirector;
        this.persistence = persistence;
        this.scheduler = scheduler;
        this.jobs = jobs;
        this.cache = cache;
        this.clock = clock;
        this.crawlerExecutors = crawlerExecutors;
        this.fileRoller = fileRoller;
        this.backupStart = backupStart;
        this.backupInterval = backupInterval;
        this.backupsLocation = backupsLocation;
        this.autoBackupsLocation = autoBackupsLocation;
    }

    @GET
    @Path("operations")
    public Model operations() {
        return addBackups(model());
    }

    @GET
    @Path("operations")
    public Model operations(@QueryParam("message") String message, @QueryParam("category") Category category) {
        return addBackups(Messages.messageModel(message, category));
    }

    @GET
    @Path("import")
    public Model batchImport(@QueryParam("message") String message, @QueryParam("category") Category category) {
        return Messages.messageModel(message, category);
    }

    @GET
    @Path("import")
    public Model batchImport() {
        return model();
    }

    @GET
    @Path("export")
    @Produces("application/json")
    public String export() {
        Map<String, Object> map = modelRepository.find(Predicates.<Record>all()).fold(new HashMap<String, Object>(), addUuidAndModel());
        return Json.toJson(map);
    }

    @POST
    @Path("import")
    public Model importJson(@FormParam("model") String batchModel) {
        try {
            Map<String, Object> uuidsAndModels = Json.parse(batchModel);
            for (Map.Entry<String, Object> entry : uuidsAndModels.entrySet()) {
                modelRepository.set(UUID.fromString(entry.getKey()), model((Map<String, Object>) entry.getValue()));
            }
            return success(format("Imported %s items", uuidsAndModels.size()));
        } catch (Exception e) {
            return error(format("Import error: %s", e.getMessage())).add("model", batchModel);
        }

    }

    @POST
    @Path("deleteAll")
    public Response deleteAll() {
        try {
            String pathname = backupNow(backupsLocation.value());
            persistence.backup(new File(pathname));
            deleteAllData();
            return redirector.seeOther(method(on(BatchResource.class).operations(format("Index has been deleted and a backup has been created: %s", pathname), Category.SUCCESS)));
        } catch (Exception e) {
            return redirector.seeOther(method(on(BatchResource.class).operations(format("Error occurred when deleting the index: %s", e.getMessage()), Category.ERROR)));
        }
    }

    @POST
    @Path("backup")
    public Response backup(@FormParam("location") String location) {
        try {
            persistence.backup(new File(location));
            return redirector.seeOther(method(on(BatchResource.class).operations(format("Index has been backed up to '%s'", location), Category.SUCCESS)));
        } catch (Exception e) {
            return redirector.seeOther(method(on(BatchResource.class).operations(format("Error occurred when backing up the index: '%s'", e.getMessage()), Category.ERROR)));
        }
    }

    @POST
    @Path("autobackup")
    public Response autobackup() {
        Response backup = backup(backupNow(autoBackupsLocation.value()));
        fileRoller.apply();
        return backup;
    }

    @POST
    @Path("delete")
    public Response delete(@FormParam("id") String id) {
        try {
            Files.delete(new File(backupsLocation.value(), id));
            return redirector.seeOther(method(on(BatchResource.class).operations(format("Deleted backed '%s'", id), Category.SUCCESS)));
        } catch (Exception e) {
            return redirector.seeOther(method(on(BatchResource.class).operations(format("Error occurred when deleting backup: '%s'", e.getMessage()), Category.ERROR)));
        }
    }

    @GET
    @Path("download")
    @Produces("application/zip")
    public Response download(@QueryParam("id") String id) {
        final File file = new File(backupsLocation.value(), id);
        if (!file.exists()) {
            return redirector.seeOther(method(on(BatchResource.class).operations(format("File not found: '%s'", id), Category.ERROR)));
        }
        return response().header("Content-Disposition", String.format("filename=%s", id)).entity(new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream) throws IOException {
                if (file.isDirectory()) {
                    zip(file, outputStream);
                }
                copy(new FileInputStream(file), outputStream);
            }
        }).build();
    }

    @POST
    @Path("restore")
    public Response restore(@FormParam("location") String location) {
        try {
            persistence.restore(new File(location));
            return redirector.seeOther(method(on(BatchResource.class).operations(format("Index has been restored from '%s'", location), Category.SUCCESS)));
        } catch (Exception e) {
            return redirector.seeOther(method(on(BatchResource.class).operations(format("Error occurred when restoring the index: '%s'", e.getMessage()), Category.ERROR)));
        }
    }

    private Callable2<? super Map<String, Object>, ? super Pair<UUID, Model>, Map<String, Object>> addUuidAndModel() {
        return new Callable2<Map<String, Object>, Pair<UUID, Model>, Map<String, Object>>() {
            public Map<String, Object> call(Map<String, Object> map, Pair<UUID, Model> pair) throws Exception {
                map.put(pair.first().toString(), pair.second());
                return map;
            }
        };
    }

    private Model addBackups(Model model) {
        return model.
                add("backupLocation", backupNow(backupsLocation.value())).
                add("backups", recursiveFiles(backupsLocation.value()).filter(hasSuffix("bgb")).map(asModel(backupsLocation.value())).toList()).
                add("id", BACKUP_JOB_ID).
                add("start", backupStart.value()).
                add("interval", backupInterval.value()).
                add("nullHack", null);

    }

    private String backupNow(File backupLocation) {
        return backupName(backupLocation, clock.now());
    }

    private static String backupName(File backupLocation, Date date) {
        return format("%s/%s.bgb", backupLocation, LEXICAL().format(date));
    }

    private void deleteAllData() throws Exception {
        persistence.deleteAll(); // Delete data twice so that no new jobs will be created
        scheduler.stop();
        jobs.deleteAll();
        persistence.deleteAll();
        crawlerExecutors.resetExecutors();
        cache.clear();
    }

    private Callable1<File, Model> asModel(final File root) {
        return new Callable1<File, Model>() {
            @Override
            public Model call(File file) throws Exception {
                return model().
                        add("name", Files.relativePath(root, file)).
                        add("location", file.getAbsolutePath()).
                        add("size", humanReadable(file.length())).
                        add("date", new Date(file.lastModified()));
            }
        };
    }

    public static String humanReadable(long bytes) {
        int unit = 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
