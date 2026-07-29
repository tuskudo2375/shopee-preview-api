import express from "express";
import * as cheerio from "cheerio";
import cors from "cors";

const app = express();

// Cho phép các trang web khác (như Google Apps Script của bạn) gọi vào API này
app.use(cors());

// Giả mạo Trình duyệt để vượt qua một số lớp bảo mật cơ bản
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36";

// HÀM BÓC TÁCH ID SẢN PHẨM TỪ MỌI ĐỊNH DẠNG LINK SHOPEE
function extractShopeeIds(url) {
  try {
    const u = new URL(url);
    
    // Dạng 1: /product/SHOPID/ITEMID
    const productMatch = u.pathname.match(/\/product\/(\d+)\/(\d+)/);
    if (productMatch) return { shopId: productMatch[1], itemId: productMatch[2] };

    // Dạng 2: -i.SHOPID.ITEMID
    const oldMatch = u.pathname.match(/-i\.(\d+)\.(\d+)/);
    if (oldMatch) return { shopId: oldMatch[1], itemId: oldMatch[2] };

    // Dạng 3 (MỚI CỦA BẠN): /opaanlp/SHOPID/ITEMID
    const opaanlpMatch = u.pathname.match(/\/opaanlp\/(\d+)\/(\d+)/);
    if (opaanlpMatch) return { shopId: opaanlpMatch[1], itemId: opaanlpMatch[2] };

    // Dạng 4 (Dự phòng cho các trang Landing Page Affiliate khác): /universal-link/SHOPID/ITEMID
    const univMatch = u.pathname.match(/\/[a-zA-Z0-9-]+\/(\d+)\/(\d+)/);
    if (univMatch && univMatch[1].length > 5 && univMatch[2].length > 5) {
       return { shopId: univMatch[1], itemId: univMatch[2] };
    }

    return { shopId: null, itemId: null };
  } catch (e) {
    return { shopId: null, itemId: null };
  }
}

// HÀM TÌM THÔNG TIN TỪ CÁC THẺ META CỦA HTML
function pickMeta($, key) {
  return $(`meta[property="${key}"]`).attr("content") || $(`meta[name="${key}"]`).attr("content") || "";
}

// HÀM LÕI: LẤY PREVIEW TỪ SHOPEE
async function getShopeePreview(shortUrl) {
  // BƯỚC 1: Giải mã link rút gọn để lấy link gốc dài ngoằng
  const res = await fetch(shortUrl, {
    redirect: "follow",
    headers: { "user-agent": UA, "accept-language": "vi-VN,vi;q=0.9,en;q=0.8" },
  });

  let finalUrl = res.url;
  const html = await res.text();
  const $ = cheerio.load(html);
  
  // Trích xuất ID
  const ids = extractShopeeIds(finalUrl);

  // Đọc thẻ HTML
  let title = pickMeta($, "og:title") || $("title").text().trim();
  let image = pickMeta($, "og:image") || pickMeta($, "twitter:image");

  // BƯỚC 2: PHƯƠNG ÁN CHỐNG BOT (Mũi nhọn mạnh nhất)
  // Nếu HTML trả về trang rác (không có ảnh hoặc tên bị chung chung), chọc thẳng vào API Shopee
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
      console.error("Lỗi khi gọi API nội bộ Shopee:", e.message);
    }
  }

  // BƯỚC 3: PHƯƠNG ÁN DỰ PHÒNG CUỐI CÙNG 
  // Nếu cả API cũng thất bại, tự động bóc tên sản phẩm từ chính đường link URL
  if (!title || title.includes("Shopee") || title === "") {
      const slugMatch = finalUrl.match(/shopee\.vn\/([^?]+)-i\./i);
      if(slugMatch) {
          title = decodeURIComponent(slugMatch[1]).replace(/-/g, ' ').toUpperCase();
      } else {
          title = "Sản phẩm Shopee";
      }
  }

  return {
    inputUrl: shortUrl,
    finalUrl: finalUrl,
    shopId: ids.shopId,
    itemId: ids.itemId,
    title: title,
    image: image,
  };
}

// KHỞI TẠO CỔNG API LẮNG NGHE YÊU CẦU TỪ APP CỦA BẠN
app.get("/preview", async (req, res) => {
  try {
    const url = req.query.url;
    
    if (!url || typeof url !== "string") {
      return res.status(400).json({ error: "Thiếu URL cần xử lý." });
    }

    const data = await getShopeePreview(url);
    res.json(data);
    
  } catch (err) {
    res.status(500).json({ error: "Máy chủ gặp lỗi khi xử lý link.", detail: err.message });
  }
});

// CHẠY SERVER
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Hệ thống Preview API đang chạy trơn tru tại cổng ${PORT}`);
});
