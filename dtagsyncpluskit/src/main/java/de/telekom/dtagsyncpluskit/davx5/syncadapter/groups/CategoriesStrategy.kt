package de.telekom.dtagsyncpluskit.davx5.syncadapter.groups

import at.bitfire.vcard4android.Contact
import de.telekom.dtagsyncpluskit.davx5.log.Logger
import de.telekom.dtagsyncpluskit.davx5.resource.LocalAddressBook


class CategoriesStrategy(val addressBook: LocalAddressBook): ContactGroupStrategy {

    override fun beforeUploadDirty() {
        // groups with DELETED=1: set all members to dirty, then remove group
        for (group in addressBook.findDeletedGroups()) {
            Logger.log.fine("Finally removing group $group")
            group.markMembersDirty()
            group.delete()
        }

        // groups with DIRTY=1: mark all members as dirty, then clean DIRTY flag of group
        for (group in addressBook.findDirtyGroups()) {
            Logger.log.fine("Marking members of modified group $group as dirty")
            group.markMembersDirty()
            group.clearDirty(null, null)
        }
    }

    override fun verifyContactBeforeSaving(contact: Contact) {
        if (contact.group || contact.members.isNotEmpty()) {
            Logger.log.warning("Received group vCard although group method is CATEGORIES. Saving as regular contact")
            contact.group = false
            contact.members.clear()
        }
    }

    override fun postProcess() {
        Logger.log.warning("PostProcess of CategoriesStrategy")
        Logger.log.info("Removing empty groups")
        addressBook.removeEmptyGroups()
    }

}