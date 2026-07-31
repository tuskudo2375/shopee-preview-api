import express from "express";
import * as cheerio from "cheerio";
import cors from "cors";

const app = express();
app.use(cors()); 

const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36";

// Hàm bóc tách ID siêu chuẩn
function extractShopeeIds(url) {
  try {
    const u = new URL(url);
    const productMatch = u.pathname.match(/\/product\/(\d+)\/(\d+)/);
    if (productMatch) return { shopId: productMatch[1], itemId: productMatch[2] };

    const oldMatch = u.pathname.match(/-i\.(\d+)\.(\d+)/);
    if (oldMatch) return { shopId: oldMatch[1], itemId: oldMatch[2] };

    const opaanlpMatch = u.pathname.match(/\/opaanlp\/(\d+)\/(\d+)/);
    if (opaanlpMatch) return { shopId: opaanlpMatch[1], itemId: opaanlpMatch[2] };

    return { shopId: null, itemId: null };
  } catch (e) { return { shopId: null, itemId: null }; }
}

async function getShopeePreview(shortUrl) {
  try {
    // 1. RENDER GIẢI MÃ LINK RÚT GỌN / LINK TRACKING
    const res = await fetch(shortUrl, {
      redirect: "follow",
      headers: { "User-Agent": UA },
    });

    const finalUrl = res.url;
    const ids = extractShopeeIds(finalUrl);

    let title = "Sản phẩm Shopee";
    let image = "";

    // 2. KẾT HỢP MICROLINK API BẰNG LINK GỐC ĐÃ LÀM SẠCH
    if (ids.shopId && ids.itemId) {
      // Ghép thành link sản phẩm chuẩn để Microlink dễ đọc
      const realProductUrl = `https://shopee.vn/product/${ids.shopId}/${ids.itemId}`;
      
      try {
        // Gọi Microlink với Cầu dao tự ngắt 6 giây chống treo Server
        const mlRes = await fetch(`https://api.microlink.io/?url=${encodeURIComponent(realProductUrl)}`, {
            signal: AbortSignal.timeout(6000) 
        });
        const mlData = await mlRes.json();
        
        if (mlData.status === 'success' && mlData.data) {
            if (mlData.data.title && !mlData.data.title.includes("Shopee Việt Nam")) {
                title = mlData.data.title;
            }
            if (mlData.data.image && mlData.data.image.url) {
                image = mlData.data.image.url;
            }
        }
      } catch (e) {
        console.log("Microlink phản hồi chậm hoặc bị lỗi ngắt kết nối");
      }
    }

    // 3. PHƯƠNG ÁN DỰ PHÒNG CHỐNG CHÁY: Tự bóc tên từ URL
    if (title === "Sản phẩm Shopee" || title === "") {
        const slugMatch = finalUrl.match(/shopee\.vn\/([^?]+)-i\./i);
        if (slugMatch) {
            title = decodeURIComponent(slugMatch[1]).replace(/-/g, ' ').toUpperCase();
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
    if (!url) return res.status(400).json({ error: "Thiếu URL" });
    
    const data = await getShopeePreview(url);
    res.json(data);
  } catch (err) {
    res.status(500).json({ error: "Lỗi Server" });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Siêu máy chủ đã chạy tại cổng ${PORT}`));
