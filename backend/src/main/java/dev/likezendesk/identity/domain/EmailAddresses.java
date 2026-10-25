package dev.likezendesk.identity.domain;

import java.util.Locale;

public final class EmailAddresses {

    private EmailAddresses() {}

    /** 保存と照合の前に必ず通す。大文字・小文字の違いで別のユーザーにならないようにする。 */
    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
