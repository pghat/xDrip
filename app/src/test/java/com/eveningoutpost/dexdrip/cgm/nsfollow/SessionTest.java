package com.eveningoutpost.dexdrip.cgm.nsfollow;

import static com.google.common.truth.Truth.assertThat;

import com.eveningoutpost.dexdrip.RobolectricTestWithConfig;
import com.eveningoutpost.dexdrip.cgm.nsfollow.messages.Entry;
import com.google.gson.internal.LinkedTreeMap;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.ResponseBody;

/**
 * Tests for {@link Session#populate(Object)}.
 * <p>
 * Historically this method blindly cast the whole response body to
 * {@code List<Entry>} based only on the first element's type. When Retrofit/Gson's
 * generic type information for {@code Call<List<Entry>>} was erased (e.g. by
 * ProGuard/R8 stripping the {@code Signature} attribute in release builds), Gson
 * deserialized the JSON array into a {@code List<LinkedTreeMap>} instead of
 * {@code List<Entry>}. Casting that list - or an individual non-{@code Entry}
 * element within an otherwise-valid list - threw a {@link ClassCastException}
 * inside {@code NightscoutCallback.onResponse()}.
 * <p>
 * Extends {@link RobolectricTestWithConfig} because {@code Session.populate()}
 * logs unexpected item types via {@code UserError.Log}, which needs ActiveAndroid
 * initialized to persist the log record.
 */
public class SessionTest extends RobolectricTestWithConfig {

    @Test
    public void populate_properEntryList_populatesEntries() {
        // :: Setup
        final Session session = new Session();
        final Entry first = new Entry();
        first.sgv = 120;
        final Entry second = new Entry();
        second.sgv = 130;
        final List<Entry> entries = Arrays.asList(first, second);

        // :: Act
        session.populate(entries);

        // :: Verify
        assertThat(session.entries).containsExactly(first, second);
        assertThat(session.treatments).isNull();
    }

    @Test
    public void populate_listOfNonEntryObjects_doesNotThrowAndYieldsEmptyEntries() {
        // Simulates a type-erased Gson response: every element deserialized as a
        // LinkedTreeMap instead of Entry.
        // :: Setup
        final Session session = new Session();
        final List<Object> rawItems = new ArrayList<>();
        rawItems.add(new LinkedTreeMap<String, Object>());
        rawItems.add(new LinkedTreeMap<String, Object>());

        // :: Act
        session.populate(rawItems);

        // :: Verify - no ClassCastException, and no bogus entries are kept
        assertThat(session.entries).isNotNull();
        assertThat(session.entries).isEmpty();
    }

    @Test
    public void populate_mixedListOfEntriesAndUnexpectedTypes_keepsOnlyValidEntries() {
        // :: Setup
        final Session session = new Session();
        final Entry valid = new Entry();
        valid.sgv = 150;
        final List<Object> mixed = new ArrayList<>();
        mixed.add(new LinkedTreeMap<String, Object>()); // type-erased element
        mixed.add(valid);
        mixed.add(null); // an explicit null in the list should also be tolerated
        mixed.add("unexpected-string");

        // :: Act
        session.populate(mixed);

        // :: Verify
        assertThat(session.entries).containsExactly(valid);
    }

    @Test
    public void populate_responseBody_populatesTreatmentsOnly() {
        // :: Setup
        final Session session = new Session();
        final ResponseBody body = ResponseBody.create(MediaType.parse("application/json"), "[]");

        // :: Act
        session.populate(body);

        // :: Verify
        assertThat(session.treatments).isSameInstanceAs(body);
        assertThat(session.entries).isNull();
    }

    @Test
    public void populate_unrelatedObjectType_leavesStateUnchanged() {
        // :: Setup
        final Session session = new Session();

        // :: Act
        session.populate("not a list or response body");

        // :: Verify - no exception, nothing populated
        assertThat(session.entries).isNull();
        assertThat(session.treatments).isNull();
    }
}
