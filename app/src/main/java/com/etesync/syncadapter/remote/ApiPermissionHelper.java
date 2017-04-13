package com.etesync.syncadapter.remote;
/*
 * Copyright (C) 2013-2015 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Binder;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.utils.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Abstract service class for remote APIs that handle app registration and user input.
 */
public class ApiPermissionHelper {

    private static final String FILE_API = "file_api_";

    private final Context mContext;
    private PackageManager mPackageManager;

    public ApiPermissionHelper(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
    }

    public static class WrongPackageCertificateException extends Exception {
        private static final long serialVersionUID = -8294642703122196028L;

        public WrongPackageCertificateException(String message) {
            super(message);
        }
    }

    /**
     * Returns true iff the caller is allowed, or false on any type of problem.
     * This method should only be used in cases where error handling is dealt with separately.
     */
    public boolean isAllowedIgnoreErrors(String journalType) {
        try {
            return isCallerAllowed(journalType);
        } catch (WrongPackageCertificateException e) {
            return false;
        }
    }

    private static byte[] getPackageCertificate(Context context, String packageName) throws NameNotFoundException {
        @SuppressLint("PackageManagerGetSignatures") // we do check the byte array of *all* signatures
                PackageInfo pkgInfo = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
        // NOTE: Silly Android API naming: Signatures are actually certificates
        Signature[] certificates = pkgInfo.signatures;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (Signature cert : certificates) {
            try {
                outputStream.write(cert.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("Should not happen! Writing ByteArrayOutputStream to concat certificates failed");
            }
        }

        // Even if an apk has several certificates, these certificates should never change
        // Google Play does not allow the introduction of new certificates into an existing apk
        // Also see this attack: http://stackoverflow.com/a/10567852
        return outputStream.toByteArray();
    }

    /**
     * Returns package name associated with the UID, which is assigned to the process that sent you the
     * current transaction that is being processed :)
     *
     * @return package name
     */
    protected String getCurrentCallingPackage() {
        String[] callingPackages = mPackageManager.getPackagesForUid(Binder.getCallingUid());

        // NOTE: No support for sharedUserIds
        // callingPackages contains more than one entry when sharedUserId has been used
        // No plans to support sharedUserIds due to many bugs connected to them:
        // http://java-hamster.blogspot.de/2010/05/androids-shareduserid.html
        String currentPkg = callingPackages[0];
        App.log.info("currentPkg: " + currentPkg);

        return currentPkg;
    }

    /**
     * Checks if process that binds to this service (i.e. the package name corresponding to the
     * process) is in the list of allowed package names.
     *
     * @return true if process is allowed to use this service
     * @throws WrongPackageCertificateException
     */
    public boolean isCallerAllowed(String journalType) throws WrongPackageCertificateException {
        return isUidAllowed(Binder.getCallingUid(), journalType);
    }

    private boolean isUidAllowed(int uid, String journalType)
            throws WrongPackageCertificateException {

        String[] callingPackages = mPackageManager.getPackagesForUid(uid);

        // is calling package allowed to use this service?
        for (String currentPkg : callingPackages) {
            if (isPackageAllowed(currentPkg, journalType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if packageName is a registered app for the API. Does not return true for own package!
     *
     * @throws WrongPackageCertificateException
     */
    public boolean isPackageAllowed(String packageName, String journalType) throws WrongPackageCertificateException {
        byte[] storedPackageCert = getCertificate(mContext, packageName, journalType);

        boolean isKnownPackage = storedPackageCert != null;
        if (!isKnownPackage) {
            App.log.warning("Package is NOT allowed! packageName: " + packageName + " for journal type " + journalType);
            return false;
        }
        App.log.info("Package is allowed! packageName: " + packageName + " for journal type " + journalType);

        byte[] currentPackageCert;
        try {
            currentPackageCert = getPackageCertificate(mContext, packageName);
        } catch (NameNotFoundException e) {
            throw new WrongPackageCertificateException(e.getMessage());
        }

        boolean packageCertMatchesStored = Arrays.equals(currentPackageCert, storedPackageCert);
        if (packageCertMatchesStored) {
            App.log.info("Package certificate matches expected.");
            return true;
        }

        throw new WrongPackageCertificateException("PACKAGE NOT ALLOWED DUE TO CERTIFICATE MISMATCH!");
    }

    public static void addCertificate(Context context, String packageName, String journalType) {
        SharedPreferences sharedPref = context.getSharedPreferences(FILE_API,
                Context.MODE_PRIVATE);
        try {
            sharedPref.edit().putString(getEncodedName(packageName, journalType),
                    Base64.encodeToString(getPackageCertificate(context, packageName), Base64.DEFAULT)).apply();
            App.log.info("Adding permission for package:" + packageName  + " for journal type " + journalType);
        } catch (NameNotFoundException aE) {
            aE.printStackTrace();
        }
    }

    private static byte[] getCertificate(Context context, String packageName, String journalType) {
        SharedPreferences sharedPref = context.getSharedPreferences(FILE_API,
                Context.MODE_PRIVATE);
        String cert = sharedPref.getString(getEncodedName(packageName, journalType), null);
        return cert == null ? null : Base64.decode(cert, Base64.DEFAULT);
    }

    private static String getEncodedName(String packageName, String journalType) {
        return packageName + "." + journalType;
    }
}