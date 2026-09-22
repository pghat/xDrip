package com.eveningoutpost.dexdrip.cgm.nsfollow;

import com.eveningoutpost.dexdrip.cgm.nsfollow.messages.Entry;
import com.eveningoutpost.dexdrip.cgm.nsfollow.utils.NightscoutUrl;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utils.framework.RetrofitService.BaseCallback;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;

/**
 *  jamorham
 *
 *  Session object manages a session with Nightscout
 */


public class Session {

    public NightscoutUrl url;
    public BaseCallback<List<Entry>> entriesCallback;
    public BaseCallback<ResponseBody> treatmentsCallback;


    // most recent set of entries
    public List<Entry> entries;
    // most recent treatments raw json
    public ResponseBody treatments;


    // populate session data from a response object which could be any supported type
    public void populate(final Object object) {
        if (object instanceof List<?>) {
            final List<?> responseItems = (List<?>) object;
            final List<Entry> validEntries = new ArrayList<>(responseItems.size());

            for (Object item : responseItems) {
                if (item instanceof Entry) {
                    validEntries.add((Entry) item);
                } else if (item != null) {
                    UserError.Log.e("NightscoutFollow",
                            "Unexpected entries item type: "
                                    + item.getClass().getName());
                }
            }

            entries = validEntries;

        } else if (object instanceof ResponseBody) {
            treatments = (ResponseBody)object;
        }
    }

}
