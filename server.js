import express from "express";
import * as cheerio from "cheerio";
import cors from "cors";

const app = express();
app.use(cors());

// Khóa API LinkPreview mà bạn đã cung cấp!
const LINKPREVIEW_KEY = "b41876e4602c4acc117320d63fb38935";
const UA = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

// Bóc tách ID
function extractShopeeIds(url) {
  try {
    const u = new URL(url);
    const productMatch = u.pathname.match(/\/product\/(\d+)\/(\d+)/);
    if (productMatch) return { shopId: productMatch[1], itemId: productMatch[2] };

    const oldMatch = u.pathname.match(/-i\.(\d+)\.(\d+)/);
    if (oldMatch) return { shopId: oldMatch[1], itemId: oldMatch[2] };

    const opaanlpMatch = u.pathname.match(/\/opaanlp\/(\d+)\/(\d+)/);
    if (opaanlpMatch) return { shopId: opaanlpMatch[1], itemId: opaanlpMatch[2] };

    const univMatch = u.pathname.match(/\/[a-zA-Z0-9-]+\/(\d+)\/(\d+)/);
    if (univMatch && univMatch[1].length > 5 && univMatch[2].length > 5) {
       return { shopId: univMatch[1], itemId: univMatch[2] };
    }
    return { shopId: null, itemId: null };
  } catch (e) { return { shopId: null, itemId: null }; }
}

async function getShopeePreview(shortUrl) {
  try {
    // 1. Bung link rút gọn
    const res = await fetch(shortUrl, {
      redirect: "follow",
      headers: { "User-Agent": UA },
    });

    const finalUrl = res.url;
    const ids = extractShopeeIds(finalUrl);

    let title = "";
    let image = "";

    // 2. KẾT HỢP SỨC MẠNH: Dùng ID ghép thành Link Gốc và cho LinkPreview quét
    if (ids.shopId && ids.itemId) {
      const realProductUrl = `https://shopee.vn/product/${ids.shopId}/${ids.itemId}`;
      
      // Mũi nhọn 1: Dùng API LinkPreview của bạn (Mạnh nhất)
      try {
        const lpRes = await fetch(`https://api.linkpreview.net/?key=${LINKPREVIEW_KEY}&q=${encodeURIComponent(realProductUrl)}`);
        const lpData = await lpRes.json();
        
        if (lpData.title && !lpData.title.includes("Shopee Việt Nam") && !lpData.title.includes("Mua và Bán")) {
          title = lpData.title;
          image = lpData.image;
        }
      } catch (e) { console.log("LinkPreview bị nghẽn"); }

      // Mũi nhọn 2: Dự phòng API Dub.co miễn phí (Nếu LinkPreview hết lượt)
      if (!title || !image) {
        try {
          const dubRes = await fetch(`https://api.dub.co/metatags?url=${encodeURIComponent(realProductUrl)}`);
          const dubData = await dubRes.json();
          if (dubData.title && !dubData.title.includes("Shopee Việt Nam")) {
            title = dubData.title;
            image = dubData.image;
          }
        } catch(e) {}
      }

      // Mũi nhọn 3: Dự phòng chọc thẳng API Shopee
      if (!title || !image) {
        try {
          const apiRes = await fetch(`https://shopee.vn/api/v4/item/get?itemid=${ids.itemId}&shopid=${ids.shopId}`, {
            headers: { "User-Agent": UA }
          });
          const apiJson = await apiRes.json();
          if (apiJson && apiJson.data) {
            title = apiJson.data.name || title;
            image = apiJson.data.image ? `https://cf.shopee.vn/file/${apiJson.data.image}` : image;
          }
        } catch (e) {}
      }
    }

    // 3. Phương án vét đáy: Tự chế tên từ link nếu cả 3 mũi nhọn đều xịt
    if (!title || title.includes("Shopee") || title === "") {
        const slugMatch = finalUrl.match(/shopee\.vn\/([^?]+)-i\./i);
        if(slugMatch) {
            title = decodeURIComponent(slugMatch[1]).replace(/-/g, ' ');
            title = title.charAt(0).toUpperCase() + title.slice(1);
        } else {
            title = "Sản phẩm Shopee";
        }
    }

    return { finalUrl, title, image, shopId: ids.shopId, itemId: ids.itemId };
  } catch (error) {
    return { finalUrl: shortUrl, title: "Sản phẩm Shopee", image: "", error: error.message };
  }
}

app.get("/preview", async (req, res) => {
  try {
    const url = req.query.url;
    if (!url || typeof url !== "string") return res.status(400).json({ error: "Thiếu URL" });
    const data = await getShopeePreview(url);
    res.json(data);
  } catch (err) {
    res.status(500).json({ error: "Lỗi Server" });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`API chạy tại cổng ${PORT}`));
