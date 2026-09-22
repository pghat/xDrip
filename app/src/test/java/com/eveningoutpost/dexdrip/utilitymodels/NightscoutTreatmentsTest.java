package com.eveningoutpost.dexdrip.utilitymodels;

import static com.google.common.truth.Truth.assertThat;

import com.eveningoutpost.dexdrip.RobolectricTestWithConfig;
import com.eveningoutpost.dexdrip.models.Treatments;

import org.junit.Test;

/**
 * Regression tests for {@link NightscoutTreatments#processTreatmentResponse(String)}.
 * <p>
 * This method is shared by both the Nightscout Follow treatments callback
 * ({@code NightscoutFollow.work()}) and the cloud uploader's REST treatment
 * download ({@code NightscoutUploader#doRESTtreatmentDownload}). Historically it
 * unconditionally cast every element of the response JSON array to
 * {@link org.json.JSONObject}, which threw a {@link ClassCastException} whenever
 * Nightscout (or a proxy in front of it) returned an array containing an explicit
 * JSON {@code null}, a primitive, or a nested array instead of an object - aborting
 * processing of the entire batch, including any otherwise-valid entries.
 */
public class NightscoutTreatmentsTest extends RobolectricTestWithConfig {

    private static String validTreatment(String id, double carbs, String createdAt) {
        return "{\"_id\":\"" + id + "\",\"eventType\":\"Carb Correction\","
                + "\"carbs\":" + carbs + ",\"insulin\":0,"
                + "\"created_at\":\"" + createdAt + "\",\"enteredBy\":\"testUser\"}";
    }

    @Test
    public void processTreatmentResponse_validResponse_createsTreatment() throws Exception {
        // :: Setup
        final String json = "[" + validTreatment("nstreat-valid-1", 30, "2020-08-17T20:27:36.075Z") + "]";

        // :: Act
        final boolean result = NightscoutTreatments.processTreatmentResponse(json);

        // :: Verify
        assertThat(result).isTrue();
        final Treatments saved = Treatments.byuuid("nstreat-valid-1");
        assertThat(saved).isNotNull();
        assertThat(saved.carbs).isEqualTo(30.0);
    }

    @Test
    public void processTreatmentResponse_emptyArray_returnsFalseWithoutThrowing() throws Exception {
        // :: Act
        final boolean result = NightscoutTreatments.processTreatmentResponse("[]");

        // :: Verify
        assertThat(result).isFalse();
    }

    @Test
    public void processTreatmentResponse_explicitNullEntry_doesNotThrowClassCastException() throws Exception {
        // Reproduces the originally reported crash: an explicit JSON null element
        // in the treatments array used to be blindly cast to JSONObject.
        // :: Setup
        final String json = "[null," + validTreatment("nstreat-after-null", 12, "2020-08-17T21:00:00.000Z") + "]";

        // :: Act
        final boolean result = NightscoutTreatments.processTreatmentResponse(json);

        // :: Verify - no exception, and the valid entry after the null is still processed
        assertThat(result).isTrue();
        assertThat(Treatments.byuuid("nstreat-after-null")).isNotNull();
    }

    @Test
    public void processTreatmentResponse_unexpectedValueTypes_skipsGracefullyAndProcessesValidEntries() throws Exception {
        // Covers non-object elements of every JSON type: string, number, boolean, nested array.
        // :: Setup
        final String json = "[\"unexpected-string\", 42, true, [\"nested\",\"array\"], "
                + validTreatment("nstreat-mixed-types", 8, "2020-08-17T22:00:00.000Z") + "]";

        // :: Act
        final boolean result = NightscoutTreatments.processTreatmentResponse(json);

        // :: Verify
        assertThat(result).isTrue();
        assertThat(Treatments.byuuid("nstreat-mixed-types")).isNotNull();
    }

    @Test
    public void processTreatmentResponse_allEntriesMalformed_returnsFalseWithoutThrowing() throws Exception {
        // :: Setup
        final String json = "[null, \"oops\", 123, [1,2,3]]";

        // :: Act
        final boolean result = NightscoutTreatments.processTreatmentResponse(json);

        // :: Verify
        assertThat(result).isFalse();
    }

    @Test
    public void processTreatmentResponse_entryMissingUuidAndId_isSkippedButOthersProcessed() throws Exception {
        // A record without "_id" and without "uuid" previously threw an uncaught
        // JSONException from inside the loop (NullPointerException-adjacent get()
        // failure), aborting the whole batch. It should now just be skipped.
        // :: Setup
        final String missingIdRecord = "{\"eventType\":\"Missing Id\",\"carbs\":10,"
                + "\"created_at\":\"2020-08-17T23:00:00.000Z\"}";
        final String json = "[" + missingIdRecord + ","
                + validTreatment("nstreat-after-missing-id", 15, "2020-08-17T23:30:00.000Z") + "]";

        // :: Act
        final boolean result = NightscoutTreatments.processTreatmentResponse(json);

        // :: Verify
        assertThat(result).isTrue();
        assertThat(Treatments.byuuid("nstreat-after-missing-id")).isNotNull();
    }

    @Test
    public void processTreatmentResponse_malformedEntriesInterspersedWithValidOnes_recoversForEach() throws Exception {
        // :: Setup
        final String json = "["
                + validTreatment("nstreat-first", 5, "2020-08-18T01:00:00.000Z") + ","
                + "null,"
                + validTreatment("nstreat-second", 6, "2020-08-18T02:00:00.000Z") + ","
                + "\"garbage\","
                + validTreatment("nstreat-third", 7, "2020-08-18T03:00:00.000Z")
                + "]";

        // :: Act
        final boolean result = NightscoutTreatments.processTreatmentResponse(json);

        // :: Verify
        assertThat(result).isTrue();
        assertThat(Treatments.byuuid("nstreat-first")).isNotNull();
        assertThat(Treatments.byuuid("nstreat-second")).isNotNull();
        assertThat(Treatments.byuuid("nstreat-third")).isNotNull();
    }
}
