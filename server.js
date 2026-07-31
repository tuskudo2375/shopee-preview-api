import express from "express";
import cors from "cors";

const app = express();
app.use(cors());

// Khóa API LinkPreview của bạn (Làm phương án dự phòng)
const LP_KEY = "b41876e4602c4acc117320d63fb38935";

// Hàm bóc tách ID siêu chuẩn từ Shopee
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
    // 1. Bung link rút gọn
    const res = await fetch(shortUrl, {
      redirect: "follow",
      headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36" },
    });
    
    let finalUrl = res.url;
    const html = await res.text();
    
    // Đọc chuyển hướng ngầm của Shopee
    const jsMatch = html.match(/window\.location\.href\s*=\s*['"]([^'"]+)['"]/);
    if (jsMatch) finalUrl = jsMatch[1];

    const ids = extractShopeeIds(finalUrl);

    let title = "Sản phẩm Shopee";
    let image = "";

    // 2. KẾT HỢP MICROLINK VÀ LINKPREVIEW ĐỂ QUÉT LINK SẢN PHẨM CHUẨN
    if (ids.shopId && ids.itemId) {
      const realUrl = `https://shopee.vn/product/${ids.shopId}/${ids.itemId}`;
      
      // Mũi nhọn 1: Dùng Microlink (Bật chế độ prerender để chạy Javascript ngầm)
      try {
        const mlRes = await fetch(`https://api.microlink.io/?url=${encodeURIComponent(realUrl)}&prerender=true`, {
            signal: AbortSignal.timeout(7000) // Cầu dao 7 giây chống treo
        });
        const mlData = await mlRes.json();
        if (mlData.status === 'success' && mlData.data) {
            if (mlData.data.title && !mlData.data.title.includes("Shopee")) title = mlData.data.title;
            if (mlData.data.image && mlData.data.image.url) image = mlData.data.image.url;
        }
      } catch (e) { console.log("Microlink quá tải"); }

      // Mũi nhọn 2: Dùng API LinkPreview của bạn (Nếu Microlink thất bại)
      if (title === "Sản phẩm Shopee" || image === "") {
         try {
             const lpRes = await fetch(`https://api.linkpreview.net/?key=${LP_KEY}&q=${encodeURIComponent(realUrl)}`, {
                 signal: AbortSignal.timeout(5000)
             });
             const lpData = await lpRes.json();
             if (lpData.title && !lpData.title.includes("Shopee")) title = lpData.title;
             if (lpData.image) image = lpData.image;
         } catch(e) { console.log("LinkPreview quá tải"); }
      }
    }

    // 3. Tự chế Tên sản phẩm từ đường link nếu bị xịt hình ảnh
    if (title === "Sản phẩm Shopee" || title === "") {
        const slugMatch = finalUrl.match(/shopee\.vn\/([^?]+)-i\./i);
        if(slugMatch) {
            title = decodeURIComponent(slugMatch[1]).replace(/-/g, ' ').toUpperCase();
        }
    }

    return { finalUrl, title, image, shopId: ids.shopId, itemId: ids.itemId };
  } catch (error) {
    return { finalUrl: shortUrl, title: "Sản phẩm Shopee", image: "", error: error.message };
  }
}

app.get("/preview", async (req, res) => {
  const url = req.query.url;
  if (!url) return res.status(400).json({ error: "Thiếu URL" });
  const data = await getShopeePreview(url);
  res.json(data);
});

app.listen(3000, () => console.log(`API đang chạy tại cổng 3000`));
