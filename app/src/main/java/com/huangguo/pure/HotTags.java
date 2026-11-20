package com.huangguo.pure;

/** 首页「热门标签」：与站点首页 SSR 输出完全一致的 20 个标签（slug → 展示名） */
public final class HotTags {

    public static final String[] SLUGS = {
            "dushi", "gufeng", "qihuan", "xiandai", "zhichang",
            "nixi", "xiaoyuan", "lvmao", "shunv", "muzi",
            "luanlun", "xitong", "xianxia", "hougong", "dananzhu",
            "xingzhuan", "naodong", "xuanyi", "ntr", "wuxia"
    };

    public static final String[] NAMES = {
            "都市", "古风", "奇幻", "现代", "职场",
            "逆袭", "校园", "绿帽", "熟女", "母子",
            "乱伦", "系统", "仙侠", "后宫", "大男主",
            "性转", "脑洞", "悬疑", "NTR", "武侠"
    };

    public static final int COUNT = SLUGS.length;

    private HotTags() {
    }

    public static String name(int i) {
        return i >= 0 && i < COUNT ? NAMES[i] : "";
    }

    public static String slug(int i) {
        return i >= 0 && i < COUNT ? SLUGS[i] : "";
    }
}
