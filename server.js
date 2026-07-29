import express from "express";
import * as cheerio from "cheerio";
import cors from "cors"; // Khuyên dùng để trình duyệt không chặn chéo

const app = express();
app.use(cors()); // Cấp quyền CORS để file HTML của bạn gọi được

const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36";

function extractShopeeIds(url) {
  const u = new URL(url);
  const productMatch = u.pathname.match(/\/product\/(\d+)\/(\d+)/);
  if (productMatch) return { shopId: productMatch[1], itemId: productMatch[2] };

  const oldMatch = u.pathname.match(/-i\.(\d+)\.(\d+)/);
  if (oldMatch) return { shopId: oldMatch[1], itemId: oldMatch[2] };

  return { shopId: null, itemId: null };
}

function pickMeta($, key) {
  return $(`meta[property="${key}"]`).attr("content") || $(`meta[name="${key}"]`).attr("content") || "";
}

async function getShopeePreview(shortUrl) {
  // BƯỚC 1: Giải mã link rút gọn
  const res = await fetch(shortUrl, {
    redirect: "follow",
    headers: { "user-agent": UA, "accept-language": "vi-VN,vi;q=0.9,en;q=0.8" },
  });

  const finalUrl = res.url;
  const html = await res.text();
  const $ = cheerio.load(html);
  const ids = extractShopeeIds(finalUrl);

  let title = pickMeta($, "og:title") || $("title").text().trim();
  let image = pickMeta($, "og:image") || pickMeta($, "twitter:image");

  // BƯỚC 2: PHƯƠNG ÁN DỰ PHÒNG CHỐNG BOT (Mũi nhọn mạnh nhất)
  // Nếu HTML không có ảnh hoặc tiêu đề rác, gọi thẳng API Shopee bằng ID bóc được
  if ((!image || title.includes("Shopee") || title === "") && ids.itemId && ids.shopId) {
    try {
      const apiRes = await fetch(`https://shopee.vn/api/v4/item/get?itemid=${ids.itemId}&shopid=${ids.shopId}`, {
        headers: { "user-agent": UA }
      });
      const apiJson = await apiRes.json();
      if (apiJson && apiJson.data) {
        if (apiJson.data.name) title = apiJson.data.name;
        if (apiJson.data.image) image = `https://cf.shopee.vn/file/${apiJson.data.image}`;
      }
    } catch (e) {
      console.error("Lỗi gọi API Shopee:", e.message);
    }
  }

  // Phương án làm sạch tiêu đề nếu vẫn lỗi
  if (!title || title.includes("Shopee")) {
      const slugMatch = finalUrl.match(/shopee\.vn\/([^?]+)-i\./i);
      if(slugMatch) {
          title = decodeURIComponent(slugMatch[1]).replace(/-/g, ' ').toUpperCase();
      } else {
          title = "Sản phẩm Shopee";
      }
  }

  return {
    inputUrl: shortUrl,
    finalUrl,
    shopId: ids.shopId,
    itemId: ids.itemId,
    title,
    image,
  };
}

app.get("/preview", async (req, res) => {
  try {
    const url = req.query.url;
    if (!url || typeof url !== "string") {
      return res.status(400).json({ error: "Thiếu URL." });
    }
    const data = await getShopeePreview(url);
    res.json(data);
  } catch (err) {
    res.status(500).json({ error: "Lỗi hệ thống.", detail: err.message });
  }
});

app.listen(3000, () => {
  console.log("Hệ thống Preview API đang chạy tại http://localhost:3000");
});