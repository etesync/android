/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import androidx.viewpager.widget.ViewPager
import com.etesync.syncadapter.App
import com.etesync.syncadapter.BuildConfig
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import com.google.android.material.tabs.TabLayout
import ezvcard.Ezvcard
import java.io.IOException
import java.util.logging.Level

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        setSupportActionBar(findViewById<View>(R.id.toolbar) as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val viewPager = findViewById<View>(R.id.viewpager) as ViewPager
        viewPager.adapter = TabsAdapter(supportFragmentManager)

        val tabLayout = findViewById<View>(R.id.tabs) as TabLayout
        tabLayout.setupWithViewPager(viewPager)
    }

    private class ComponentInfo internal constructor(internal val title: String, internal val version: String?, internal val website: String, internal val copyright: String, internal val licenseInfo: Int, internal val licenseTextFile: String)


    private class TabsAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getCount(): Int {
            return components.size
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return components[position].title
        }

        override fun getItem(position: Int): Fragment {
            return ComponentFragment.instantiate(position)
        }
    }

    class ComponentFragment : Fragment(), LoaderManager.LoaderCallbacks<Spanned> {

        @SuppressLint("SetTextI18n")
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val info = components[arguments!!.getInt(KEY_POSITION)]

            val v = inflater.inflate(R.layout.about_component, container, false)

            var tv = v.findViewById<View>(R.id.title) as TextView
            tv.text = info.title + if (info.version != null) " " + info.version else ""

            tv = v.findViewById<View>(R.id.website) as TextView
            tv.autoLinkMask = Linkify.WEB_URLS
            tv.text = info.website

            tv = v.findViewById<View>(R.id.copyright) as TextView
            tv.text = "© " + info.copyright

            tv = v.findViewById<View>(R.id.license_info) as TextView
            tv.setText(info.licenseInfo)

            // load and format license text
            val args = Bundle(1)
            args.putString(KEY_FILE_NAME, info.licenseTextFile)
            loaderManager.initLoader(0, args, this)

            return v
        }

        override fun onCreateLoader(id: Int, args: Bundle?): Loader<Spanned> {
            return LicenseLoader(context!!, args!!.getString(KEY_FILE_NAME))
        }

        override fun onLoadFinished(loader: Loader<Spanned>, license: Spanned) {
            if (view != null) {
                val tv = view!!.findViewById<View>(R.id.license_text) as TextView?
                if (tv != null) {
                    tv.autoLinkMask = Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS
                    tv.text = license
                }
            }
        }

        override fun onLoaderReset(loader: Loader<Spanned>) {}

        companion object {
            private val KEY_POSITION = "position"
            private val KEY_FILE_NAME = "fileName"

            fun instantiate(position: Int): ComponentFragment {
                val frag = ComponentFragment()
                val args = Bundle(1)
                args.putInt(KEY_POSITION, position)
                frag.arguments = args
                return frag
            }
        }
    }

    private class LicenseLoader internal constructor(context: Context, internal val fileName: String) : AsyncTaskLoader<Spanned>(context) {
        internal var content: Spanned? = null

        override fun onStartLoading() {
            if (content == null)
                forceLoad()
            else
                deliverResult(content)
        }

        override fun loadInBackground(): Spanned? {
            Logger.log.fine("Loading license file $fileName")
            try {
                val inputStream = context.resources.assets.open(fileName)
                val raw = inputStream.readBytes()
                inputStream.close()
                content = Html.fromHtml(String(raw))
                return content
            } catch (e: IOException) {
                Logger.log.log(Level.SEVERE, "Couldn't read license file", e)
                return null
            }

        }
    }

    companion object {

        private val components = arrayOf(ComponentInfo(
                App.appName, BuildConfig.VERSION_NAME, Constants.webUri.toString(),
                "Tom Hacohen",
                R.string.about_license_info_no_warranty, "gpl-3.0-standalone.html"
        ), ComponentInfo(
                "DAVdroid", "(forked from)", "https://syncadapter.bitfire.at",
                "Ricki Hirner, Bernhard Stockmann (bitfire web engineering)",
                R.string.about_license_info_no_warranty, "gpl-3.0-standalone.html"
        ), ComponentInfo(
                "AmbilWarna", null, "https://github.com/yukuku/ambilwarna",
                "Yuku", R.string.about_license_info_no_warranty, "apache2.html"
        ), ComponentInfo(
                "Apache Commons", null, "http://commons.apache.org/",
                "Apache Software Foundation", R.string.about_license_info_no_warranty, "apache2.html"
        ), ComponentInfo(
                "dnsjava", null, "http://dnsjava.org/",
                "Brian Wellington", R.string.about_license_info_no_warranty, "bsd.html"
        ), ComponentInfo(
                "ez-vcard", Ezvcard.VERSION, "https://github.com/mangstadt/ez-vcard",
                "Michael Angstadt", R.string.about_license_info_no_warranty, "bsd.html"
        ), ComponentInfo(
                "ical4j", "2.x", "https://ical4j.github.io/",
                "Ben Fortuna", R.string.about_license_info_no_warranty, "bsd-3clause.html"
        ), ComponentInfo(
                "OkHttp", null, "https://square.github.io/okhttp/",
                "Square, Inc.", R.string.about_license_info_no_warranty, "apache2.html"
        ), ComponentInfo(
                "Project Lombok", null, "https://projectlombok.org/",
                "The Project Lombok Authors", R.string.about_license_info_no_warranty, "mit.html"
        ))
    }

}
