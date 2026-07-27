package com.hivi.launcher.account.model;

/**
 * User profile returned by the authenticated {@code user/details} endpoint.
 */
public final class AuthorizedUserInfo {
    private final String mId;
    private final String mName;
    private final String mAvatarUrl;
    private final String mArea;
    private final String mPhone;
    private final String mPreferred;
    private final String mCreateTime;
    private final int mDeleted;

    public AuthorizedUserInfo(String id, String name, String avatarUrl, String area, String phone,
            String preferred, String createTime, int deleted) {
        mId = id;
        mName = name;
        mAvatarUrl = avatarUrl;
        mArea = area;
        mPhone = phone;
        mPreferred = preferred;
        mCreateTime = createTime;
        mDeleted = deleted;
    }

    public String getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public String getAvatarUrl() {
        return mAvatarUrl;
    }

    public String getArea() {
        return mArea;
    }

    public String getPhone() {
        return mPhone;
    }

    public String getPreferred() {
        return mPreferred;
    }

    public String getCreateTime() {
        return mCreateTime;
    }

    public int getDeleted() {
        return mDeleted;
    }
}
