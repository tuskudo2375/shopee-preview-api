import express from "express";
import * as cheerio from "cheerio";
import cors from "cors";

const app = express();
app.use(cors());

// BÍ QUYẾT TỐI THƯỢNG: NGỤY TRANG THÀNH GOOGLEBOT ĐỂ SHOPEE KHÔNG DÁM CHẶN
const UA = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

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
    // Gọi Shopee với danh nghĩa Googlebot
    const res = await fetch(shortUrl, {
      redirect: "follow",
      headers: {
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "vi-VN,vi;q=0.5"
      },
    });

    const finalUrl = res.url;
    const html = await res.text();
    const $ = cheerio.load(html);
    const ids = extractShopeeIds(finalUrl);

    let title = $('meta[property="og:title"]').attr("content") || $('meta[name="twitter:title"]').attr("content") || $("title").text().trim();
    let image = $('meta[property="og:image"]').attr("content") || $('meta[name="twitter:image"]').attr("content") || "";

    // Làm sạch tiêu đề nếu Shopee trả về tên chung chung
    if (!title || title.includes("Shopee Việt Nam") || title.includes("Mua và Bán") || title === "Shopee") {
        const slugMatch = finalUrl.match(/shopee\.vn\/([^?]+)-i\./i);
        if(slugMatch) {
            title = decodeURIComponent(slugMatch[1]).replace(/-/g, ' ');
            title = title.charAt(0).toUpperCase() + title.slice(1);
        } else {
            title = "Sản phẩm Shopee";
        }
    }

    // Nếu vẫn không có ảnh (do Shopee render bằng React), dùng ID gọi thẳng API nội bộ bằng Googlebot
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
