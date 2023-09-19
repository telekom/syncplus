/**
 * This file is part of SyncPlus.
 *
 * Copyright (C) 2020  Deutsche Telekom AG
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package de.telekom.dtagsyncpluskit.davx5.resource

import android.content.ContentValues
import android.os.Build
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.RawContacts.Data
import at.bitfire.vcard4android.*
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.model.UnknownProperties
import ezvcard.Ezvcard
import java.io.FileNotFoundException
import java.util.*
import de.telekom.dtagsyncpluskit.BuildConfig
import org.apache.commons.lang3.StringUtils

class LocalContact: AndroidContact, LocalAddress {

    companion object {
        init {
            Contact.productID = "+//IDN bitfire.at//${BuildConfig.userAgent}/${BuildConfig.VERSION_NAME} ez-vcard/" + Ezvcard.VERSION
        }

        const val COLUMN_FLAGS = ContactsContract.RawContacts.SYNC4
        const val COLUMN_HASHCODE = ContactsContract.RawContacts.SYNC3
    }

    private val cachedGroupMemberships = HashSet<Long>()
    private val groupMemberships = HashSet<Long>()

    override var scheduleTag: String?
        get() = null
        set(_) = throw NotImplementedError()

    override var flags: Int = 0

    constructor(addressBook: AndroidAddressBook<LocalContact, *>, values: ContentValues)
            : super(addressBook, values) {
        flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
    }

    constructor(addressBook: AndroidAddressBook<LocalContact,*>, contact: Contact, fileName: String?, eTag: String?, flags: Int)
            : super(addressBook, contact, fileName, eTag) {
        this.flags = flags
    }


    override fun prepareForUpload(): String {
        var uid: String? = null
        addressBook.provider!!.query(rawContactSyncURI(), arrayOf(COLUMN_UID), null, null, null)?.use { cursor ->
            if (cursor.moveToNext())
                uid = StringUtils.trimToNull(cursor.getString(0))
        }

        if (uid == null) {
            // generate new UID
            uid = UUID.randomUUID().toString()

            val values = ContentValues(1)
            values.put(COLUMN_UID, uid)
            addressBook.provider!!.update(rawContactSyncURI(), values, null, null)

            _contact!!.uid = uid
        }

        return "$uid.vcf"
    }

    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            throw IllegalArgumentException("Contacts must not have a Schedule-Tag")

        val values = ContentValues(4)
        if (fileName != null)
            values.put(COLUMN_FILENAME, fileName)
        values.put(COLUMN_ETAG, eTag)
        values.put(ContactsContract.RawContacts.DIRTY, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            val hashCode = dataHashCode()
            values.put(COLUMN_HASHCODE, hashCode)
            Logger.log.finer("Clearing dirty flag with eTag = $eTag, contact hash = $hashCode")
        }

        addressBook.provider!!.update(rawContactSyncURI(), values, null, null)

        if (fileName != null)
            this.fileName = fileName
        this.eTag = eTag
    }

    override fun resetDeleted() {
        val values = ContentValues(1)
        values.put(ContactsContract.Groups.DELETED, 0)
        addressBook.provider!!.update(rawContactSyncURI(), values, null, null)
    }

    fun resetDirty() {
        val values = ContentValues(1)
        values.put(ContactsContract.RawContacts.DIRTY, 0)
        addressBook.provider!!.update(rawContactSyncURI(), values, null, null)
    }

    override fun updateFlags(flags: Int) {
        val values = ContentValues(1)
        values.put(COLUMN_FLAGS, flags)
        addressBook.provider!!.update(rawContactSyncURI(), values, null, null)

        this.flags = flags
    }


/*    override fun populateData(mimeType: String, row: ContentValues) {
        when (mimeType) {
            CachedGroupMembership.CONTENT_ITEM_TYPE ->
                cachedGroupMemberships += row.getAsLong(CachedGroupMembership.GROUP_ID)
            GroupMembership.CONTENT_ITEM_TYPE ->
                groupMemberships += row.getAsLong(GroupMembership.GROUP_ROW_ID)
            UnknownProperties.CONTENT_ITEM_TYPE ->
                _contact!!.unknownProperties = row.getAsString(UnknownProperties.UNKNOWN_PROPERTIES)
        }
    }*/

/*    override fun insertDataRows(batch: BatchOperation) {
        super.insertDataRows(batch)

        _contact!!.unknownProperties?.let { unknownProperties ->
            val builder = insertDataBuilder()
                .withValue(UnknownProperties.MIMETYPE, UnknownProperties.CONTENT_ITEM_TYPE)
                .withValue(UnknownProperties.UNKNOWN_PROPERTIES, unknownProperties)
            batch.enqueue(builder)
        }
    }*/


    /**
     * Calculates a hash code from the contact's data (VCard) and group memberships.
     * Attention: re-reads {@link #contact} from the database, discarding all changes in memory
     * @return hash code of contact data (including group memberships)
     */
    internal fun dataHashCode(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("dataHashCode() should not be called on Android != 7")

        // reset contact so that getContact() reads from database
        _contact = null

        // groupMemberships is filled by getContact()
        val dataHash = _contact!!.hashCode()
        val groupHash = groupMemberships.hashCode()
        Logger.log.finest("Calculated data hash = $dataHash, group memberships hash = $groupHash")
        return dataHash xor groupHash
    }

    fun updateHashCode(batch: BatchOperation?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("updateHashCode() should not be called on Android != 7")

        val hashCode = dataHashCode()
        Logger.log.fine("Storing contact hash = $hashCode")

        if (batch == null) {
            val values = ContentValues(1)
            values.put(COLUMN_HASHCODE, hashCode)
            addressBook.provider!!.update(rawContactSyncURI(), values, null, null)
        } else
            batch.enqueue(BatchOperation.CpoBuilder
                .newUpdate(rawContactSyncURI())
                .withValue(COLUMN_HASHCODE, hashCode))
    }

    fun getLastHashCode(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("getLastHashCode() should not be called on Android != 7")

        addressBook.provider!!.query(rawContactSyncURI(), arrayOf(COLUMN_HASHCODE), null, null, null)?.use { c ->
            if (c.moveToNext() && !c.isNull(0))
                return c.getInt(0)
        }
        return 0
    }


    fun addToGroup(batch: BatchOperation, groupID: Long) {
        batch.enqueue(BatchOperation.CpoBuilder
            .newInsert(dataSyncURI())
            .withValue(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
            .withValue(GroupMembership.RAW_CONTACT_ID, id)
            .withValue(GroupMembership.GROUP_ROW_ID, groupID))
        groupMemberships += groupID

        batch.enqueue(BatchOperation.CpoBuilder
            .newInsert(dataSyncURI())
            .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
            .withValue(CachedGroupMembership.RAW_CONTACT_ID, id)
            .withValue(CachedGroupMembership.GROUP_ID, groupID)
        )
        cachedGroupMemberships += groupID
    }

    fun removeGroupMemberships(batch: BatchOperation) {
        batch.enqueue(BatchOperation.CpoBuilder
            .newDelete(dataSyncURI())
            .withSelection(
                "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?)",
                arrayOf(id.toString(), GroupMembership.CONTENT_ITEM_TYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
            ))
        groupMemberships.clear()
        cachedGroupMemberships.clear()
    }

    /**
     * Returns the IDs of all groups the contact was member of (cached memberships).
     * Cached memberships are kept in sync with memberships by DAVx5 and are used to determine
     * whether a membership has been deleted/added when a raw contact is dirty.
     * @return set of {@link GroupMembership#GROUP_ROW_ID} (may be empty)
     * @throws FileNotFoundException if the current contact can't be found
     * @throws RemoteException on contacts provider errors
     */
    fun getCachedGroupMemberships(): Set<Long> {
        _contact
        return cachedGroupMemberships
    }

    /**
     * Returns the IDs of all groups the contact is member of.
     * @return set of {@link GroupMembership#GROUP_ROW_ID}s (may be empty)
     * @throws FileNotFoundException if the current contact can't be found
     * @throws RemoteException on contacts provider errors
     */
    fun getGroupMemberships(): Set<Long> {
        _contact
        return groupMemberships
    }


    // data rows
    override fun buildContact(builder: BatchOperation.CpoBuilder, update: Boolean) {
        builder.withValue(COLUMN_FLAGS, flags)
        super.buildContact(builder, update)
    }

    // factory

    object Factory: AndroidContactFactory<LocalContact> {
        override fun fromProvider(addressBook: AndroidAddressBook<LocalContact, *>, values: ContentValues) =
            LocalContact(addressBook, values)
    }

}
