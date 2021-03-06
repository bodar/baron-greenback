package com.googlecode.barongreenback.crawler;

import com.googlecode.barongreenback.shared.RecordDefinition;
import com.googlecode.lazyrecords.Record;
import com.googlecode.totallylazy.Callables;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.Uri;
import org.w3c.dom.Document;

import java.util.concurrent.Callable;

import static com.googlecode.totallylazy.Sequences.empty;
import static com.googlecode.totallylazy.Sequences.one;
import static com.googlecode.totallylazy.Strings.isEmpty;
import static com.googlecode.totallylazy.Uri.uri;
import static com.googlecode.totallylazy.Xml.selectContents;

public class MoreDocumentFeeder implements Feeder<Document> {
    private final Feeder<Uri> uriFeeder;
    private final String moreXpath;

    public MoreDocumentFeeder(Feeder<Uri> uriFeeder, String moreXpath) {
        this.uriFeeder = uriFeeder;
        this.moreXpath = moreXpath;
    }

    public Sequence<Record> get(Document document, RecordDefinition definition) throws Exception {
        return new DocumentFeeder().get(document, definition).
                join(isEmpty(moreXpath) ? empty(Record.class) : more(document, definition));
    }

    private Sequence<Record> more(final Document document, final RecordDefinition definition) {
        final String uri = selectContents(document, moreXpath);
        return one(new Callable<Sequence<Record>>() {
            public Sequence<Record> call() throws Exception {
                if (isEmpty(uri)) {
                    return empty();
                }
                return uriFeeder.get(uri(uri), definition);
            }
        }).flatMap(Callables.<Sequence<Record>>call());
    }
}
