package com.googlecode.barongreenback.crawler;

import com.googlecode.lazyrecords.Definition;
import com.googlecode.lazyrecords.Keyword;
import com.googlecode.lazyrecords.Record;
import com.googlecode.totallylazy.*;

import static com.googlecode.barongreenback.shared.RecordDefinition.RECORD_DEFINITION;
import static com.googlecode.lazyrecords.Keywords.UNIQUE;
import static com.googlecode.lazyrecords.Keywords.metadata;
import static com.googlecode.lazyrecords.Record.constructors.record;
import static com.googlecode.totallylazy.Predicates.*;

public class Subfeeder2 {
    public static Function1<Sequence<Record>, Sequence<HttpJob>> subfeeds(final Definition destination) {
        return new Function1<Sequence<Record>, Sequence<HttpJob>>() {
            @Override
            public Sequence<HttpJob> call(Sequence<Record> records) throws Exception {
                return subfeeds(records, destination);
            }
        };
    }

    public static Sequence<HttpJob> subfeeds(Sequence<Record> records, Definition destination) {
        return records.flatMap(subfeedsKeywords(destination));
    }

    private static Callable1<Record, Sequence<HttpJob>> subfeedsKeywords(final Definition destination) {
        return new Callable1<Record, Sequence<HttpJob>>() {
            public Sequence<HttpJob> call(final Record record) throws Exception {
                Sequence<Keyword<?>> subfeedKeywords = record.keywords().filter(where(metadata(RECORD_DEFINITION), is(Predicates.notNullValue()))).realise();
                return subfeedKeywords.map(toJob(record, destination));
            }
        };
    }

    private static Callable1<Keyword<?>, HttpJob> toJob(final Record record, final Definition destination) {
        return new Callable1<Keyword<?>, HttpJob>() {
            @Override
            public HttpJob call(Keyword<?> keyword) throws Exception {
                return job(keyword, record, destination);
            }
        };
    }

    public static HttpJob job(Keyword<?> subfeedKeyword, Record record, Definition destination) {
        Object value = record.get(subfeedKeyword);
        Uri uri = Uri.uri(value.toString());

        Sequence<Pair<Keyword<?>, Object>> keysAndValues = record.fields().filter(where(Callables.<Keyword<?>>first(), where(metadata(UNIQUE), is(true)))).realise();

        return HttpJob.job(SubfeedDatasource.dataSource(uri, subfeedKeyword.metadata().get(RECORD_DEFINITION).definition(), keysAndValues), destination);
    }

    public static Sequence<Record> mergePreviousUniqueIdentifiers(Sequence<Record> records, final HttpDataSource dataSource) {
        if(dataSource instanceof SubfeedDatasource){
            return records.map(new Callable1<Record, Record>() {
                @Override
                public Record call(Record record) throws Exception {
                    return record(record.fields().join(((SubfeedDatasource) dataSource).uniqueIdentifiers()));
                }
            });
        }
        return records;
   }
}