import express from "express";
import * as cheerio from "cheerio";
import cors from "cors";

const app = express();
app.use(cors());

// Ngụy trang thành Googlebot
const UA = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

function extractShopeeIds(url) {
  try {
    const u = new URL(url);
    const productMatch = u.pathname.match(/\/product\/(\d+)\/(\d+)/);
    if (productMatch) return { shopId: productMatch[1], itemId: productMatch[2] };

    const oldMatch = u.pathname.match(/-i\.(\d+)\.(\d+)/);
    if (oldMatch) return { shopId: oldMatch[1], itemId: oldMatch[2] };

    // Dạng Tracking Page của Shopee
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
    // 1. Theo dấu đường link rút gọn
    const res = await fetch(shortUrl, {
      redirect: "follow",
      headers: { "User-Agent": UA },
    });

    const finalUrl = res.url;
    const ids = extractShopeeIds(finalUrl);

    let title = "Sản phẩm Shopee";
    let image = "";

    // 2. KHẮC PHỤC LỖI TRANG TRACKING: ÉP TRUY CẬP VÀO TRANG SẢN PHẨM GỐC
    if (ids.shopId && ids.itemId) {
      const realProductUrl = `https://shopee.vn/product/${ids.shopId}/${ids.itemId}`;
      
      try {
        // Googlebot quét thẳng vào trang sản phẩm thật để lấy thông tin
        const prodRes = await fetch(realProductUrl, {
          headers: {
            "User-Agent": UA,
            "Accept-Language": "vi-VN,vi;q=0.9"
          }
        });
        const prodHtml = await prodRes.text();
        const $ = cheerio.load(prodHtml);

        title = $('meta[property="og:title"]').attr("content") || $('meta[name="twitter:title"]').attr("content") || title;
        image = $('meta[property="og:image"]').attr("content") || $('meta[name="twitter:image"]').attr("content") || image;
      } catch(e) {
        console.log("Lỗi khi quét HTML trang sản phẩm thật:", e.message);
      }
    }

    // Lọc tiêu đề rác
    if (title.includes("Shopee Việt Nam") || title.includes("Mua và Bán") || title.length < 5) {
        title = "Sản phẩm Shopee";
    }

    // 3. Dự phòng cấp 2: Nếu HTML xịt, gọi API (Lớp phòng thủ chống DataDome)
    if ((!image || image === "") && ids.itemId && ids.shopId) {
      try {
        const apiRes = await fetch(`https://shopee.vn/api/v4/item/get?itemid=${ids.itemId}&shopid=${ids.shopId}`, {
          headers: { "User-Agent": UA }
        });
        const apiJson = await apiRes.json();
        if (apiJson && apiJson.data) {
          if (apiJson.data.name) title = apiJson.data.name;
          if (apiJson.data.image) image = `https://cf.shopee.vn/file/${apiJson.data.image}`;
        }
      } catch (e) {}
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
