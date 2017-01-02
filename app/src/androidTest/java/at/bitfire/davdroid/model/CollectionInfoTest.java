/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;

import org.junit.Test;

import at.bitfire.davdroid.model.ServiceDB.Collections;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CollectionInfoTest {

    MockWebServer server = new MockWebServer();

    @Test
    public void testFromDB() {
        ContentValues values = new ContentValues();
        values.put(Collections.ID, 1);
        values.put(Collections.SERVICE_ID, 1);
        values.put(Collections.URL, "http://example.com");
        values.put(Collections.READ_ONLY, 1);
        values.put(Collections.DISPLAY_NAME, "display name");
        values.put(Collections.DESCRIPTION, "description");
        values.put(Collections.COLOR, 0xFFFF0000);
        values.put(Collections.TIME_ZONE, "tzdata");
        values.put(Collections.SUPPORTS_VEVENT, 1);
        values.put(Collections.SUPPORTS_VTODO, 1);
        values.put(Collections.SYNC, 1);

        CollectionInfo info = CollectionInfo.fromDB(values);
        assertEquals(1, info.id);
        assertEquals(1, (long)info.serviceID);
        assertEquals("http://example.com", info.url);
        assertTrue(info.readOnly);
        assertEquals("display name", info.displayName);
        assertEquals("description", info.description);
        assertEquals(0xFFFF0000, (int)info.color);
        assertEquals("tzdata", info.timeZone);
        assertTrue(info.supportsVEVENT);
        assertTrue(info.supportsVTODO);
        assertTrue(info.selected);
    }

}
