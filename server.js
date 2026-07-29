import express from "express";
import * as cheerio from "cheerio";
import cors from "cors";

const app = express();
app.use(cors()); // Cho phép điện thoại gọi API thoải mái

// MẶT NẠ 1: Giả làm Trình duyệt web máy tính (Dùng để bung link)
const WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36";

// MẶT NẠ 2: Giả làm Ứng dụng Shopee trên điện thoại Android (Bí quyết vượt tường lửa)
const APP_UA = "Shopee/3.30.0 (Android; 10; Scale/2.00)";

// Hàm bóc tách ID siêu chuẩn từ mọi loại link
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
    // 1. BUNG LINK RÚT GỌN (Đeo mặt nạ Web)
    const res = await fetch(shortUrl, {
      redirect: "follow",
      headers: { "User-Agent": WEB_UA },
    });

    const finalUrl = res.url;
    const ids = extractShopeeIds(finalUrl);

    let title = "Sản phẩm Shopee";
    let image = "";

    // 2. TẤN CÔNG LẤY ẢNH VÀ TÊN SẢN PHẨM
    if (ids.shopId && ids.itemId) {
      const apiUrl = `https://shopee.vn/api/v4/item/get?itemid=${ids.itemId}&shopid=${ids.shopId}`;
      
      // CHIẾN THUẬT A: Đeo mặt nạ App Shopee đâm thẳng vào kho dữ liệu
      try {
        const apiRes = await fetch(apiUrl, {
          headers: {
            "User-Agent": APP_UA,
            "X-API-Source": "rn",
            "X-Shopee-Language": "vi"
          },
          signal: AbortSignal.timeout(4000) // CẦU DAO: Quá 4s tự ngắt, không để treo Server
        });
        const apiJson = await apiRes.json();
        
        if (apiJson && apiJson.data && apiJson.data.name) {
          title = apiJson.data.name;
          image = `https://cf.shopee.vn/file/${apiJson.data.image}`;
        }
      } catch (e) {
        console.log("Bị chặn ở Chiến thuật A");
      }

      // CHIẾN THUẬT B: Nếu Chiến thuật A vẫn xịt, đi đường vòng qua trạm AllOrigins
      if (title === "Sản phẩm Shopee" || image === "") {
        try {
          const proxyRes = await fetch(`https://api.allorigins.win/raw?url=${encodeURIComponent(apiUrl)}`, {
             signal: AbortSignal.timeout(5000) // Cầu dao dự phòng
          });
          const proxyJson = await proxyRes.json();
          
          if (proxyJson && proxyJson.data && proxyJson.data.name) {
            title = proxyJson.data.name;
            image = `https://cf.shopee.vn/file/${proxyJson.data.image}`;
          }
        } catch (e) {
          console.log("Bị chặn ở Chiến thuật B");
        }
      }
    }

    // 3. VÉT ĐÁY: Nếu Shopee sập hoàn toàn, tự chế Tên Sản Phẩm từ đường link
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

// BẬT MÁY CHỦ LẮNG NGHE ĐIỆN THOẠI CỦA BẠN GỌI TỚI
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
app.listen(PORT, () => console.log(`Siêu máy chủ săn sale đã chạy tại cổng ${PORT}`));
