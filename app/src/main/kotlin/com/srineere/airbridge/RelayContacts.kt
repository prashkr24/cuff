package com.srineere.airbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

/**
 * People from the phone's contacts, so a rule can be made before they call.
 *
 * Only the display name leaves this file. Numbers are turned into the same
 * identity hash a call arrives with and are never stored or reported, so a
 * contact and an incoming call agree on who they are without the number
 * living anywhere.
 */
internal object RelayContacts {
    private const val LIMIT = 2_000

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Contacts with a phone number, newest-sorted by name, one entry per
     * distinct number. A contact with two numbers appears twice, because a
     * call only ever arrives on one of them.
     */
    /**
     * The phone number behind a contacts lookup URI, which is how a dialer
     * names the caller. Null without the contacts permission, or when the
     * handle is not a lookup URI, in which case the caller is identified by
     * the handle itself and simply will not line up with a contact.
     */
    /** One entry per contact, identified the way a named caller arrives. */
    private fun byName(context: Context, packageName: String): List<Map<String, String>> {
        val found = linkedMapOf<String, String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                null, null,
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && found.size < LIMIT) {
                    val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    val id = RelayCallerIdentity.identityOf(packageName, name) ?: continue
                    found.putIfAbsent(id, name)
                }
            }
        }
        return found.entries.map { mapOf("id" to it.key, "label" to it.value, "detail" to "") }
    }

    fun numberFor(context: Context, handle: String): String? {
        if (!handle.startsWith("content://") || !granted(context)) return null
        return RelayNumberMemo.of(handle) { lookupNumber(context, handle) }
    }

    /**
     * The two provider round-trips themselves, run only on a memo miss.
     *
     * Two, not one: a lookup URI names a contact, and the number lives on a
     * row of its own that has to be asked for separately.
     */
    private fun lookupNumber(context: Context, handle: String): String? = runCatching {
        val contactId = ContactsContract.Contacts.lookupContact(
            context.contentResolver, android.net.Uri.parse(handle),
        )?.lastPathSegment ?: return@runCatching null
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId), null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** Ends the memo's life. See [RelayNumberMemo] for how long that is. */
    fun forgetNumbers() = RelayNumberMemo.forget()

    /**
     * Apps that name a caller rather than number them. WhatsApp and Telegram
     * both attach only a display name, which is the contact's own name, so a
     * contact can be matched by name. That is weaker than a number: renaming
     * the contact breaks the rule, and two people with one name cannot be
     * told apart.
     *
     * Telegram's incoming call is a `CallStyle` notification carrying a
     * `Person` built with `setName` alone -- the same shape WhatsApp posts.
     * Forks are left out because they are separate code; one that does name
     * its callers earns its list the first time it rings, through
     * [RelayAppSelection.rememberNamesCallers].
     */
    val NAMED_CALLERS = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "org.telegram.messenger", "org.telegram.messenger.web",
    )

    fun list(context: Context, packageName: String = "com.google.android.dialer"):
        List<Map<String, String>> {
        if (!granted(context)) return emptyList()
        if (packageName in NAMED_CALLERS) return byName(context, packageName)
        val found = linkedMapOf<String, Pair<String, String>>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && found.size < LIMIT) {
                    val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    val number = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val id = RelayCallerIdentity.identityOf(packageName, number) ?: continue
                    found.putIfAbsent(id, name to number)
                }
            }
        }
        return found.entries.map {
            mapOf("id" to it.key, "label" to it.value.first, "detail" to it.value.second)
        }
    }
}

/**
 * Numbers resolved while one call is ringing, and no longer than that.
 *
 * `onNotificationPosted` runs on the main looper -- the same thread that
 * draws the Compose UI -- and a dialer re-posts its ringing notification
 * about once a second for as long as the phone rings. Every one of those
 * re-posts used to spend two synchronous content-provider round trips
 * arriving at the answer it already had a second earlier, for an alert the
 * relay then dropped as "ringing" anyway.
 *
 * The lifetime is deliberately one call, and that is a decision rather than
 * an oversight: a contact edited in the middle of a ring keeps the number
 * it had when the call started, and the next call picks the change up. Do
 * not quietly promote this into a cache that outlives the ring. A permanent
 * one would hold a wrong number until the process died, which is a worse
 * fault than the one it saves. [forget] is called when a ring stops and
 * whenever a notification is taken back.
 *
 * Bounded as well as short-lived, because nothing guarantees [forget] is
 * reached: a dialer that crashes, a listener torn down mid-call. The bound
 * is the real guarantee, and it matters more here than it would elsewhere
 * -- a plain phone number in memory is the one thing the rest of this file
 * goes out of its way not to keep.
 */
internal object RelayNumberMemo {
    /** More than one call ringing at a time is already odd; eight is slack. */
    internal const val LIMIT = 8

    private val numbers = linkedMapOf<String, String?>()

    /**
     * The memoised number for [handle], or [lookup]'s answer stored for
     * next time.
     *
     * Null is remembered too. "This handle resolves to nothing" costs both
     * queries to arrive at and is the ordinary answer for somebody who is
     * not in contacts, so it is the one worth not asking twice.
     */
    @Synchronized
    fun of(handle: String, lookup: () -> String?): String? {
        if (numbers.containsKey(handle)) return numbers[handle]
        val number = lookup()
        numbers[handle] = number
        // Oldest out first, the way RelayRecentCallers drops its oldest.
        while (numbers.size > LIMIT) numbers.remove(numbers.keys.first())
        return number
    }

    @Synchronized
    fun forget() = numbers.clear()
}
