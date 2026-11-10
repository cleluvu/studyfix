// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

export function cryptoRandomId() {
  return (
    globalThis.crypto?.randomUUID?.() ||
    `${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

export async function sha256(message) {
  if (globalThis.crypto?.subtle && globalThis.isSecureContext) {
    const buf = await crypto.subtle.digest(
      "SHA-256",
      new TextEncoder().encode(message),
    );
    return Array.from(new Uint8Array(buf))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
  }
  // Tự tính SHA-256 khi chạy thử qua HTTP trong mạng LAN và trình duyệt không hỗ trợ sẵn.
  const K = [
    1116352408, 1899447441, 3049323471, 3921009573, 961987163, 1508970993,
    2453635748, 2870763221, 3624381080, 310598401, 607225278, 1426881987,
    1925078388, 2162078206, 2614888103, 3248222580, 3835390401, 4022224774,
    264347078, 604807628, 770255983, 1249150122, 1555081692, 1996064986,
    2554220882, 2821834349, 2952996808, 3210313671, 3336571891, 3584528711,
    113926993, 338241895, 666307205, 773529912, 1294757372, 1396182291,
    1695183700, 1986661051, 2177026350, 2456956037, 2730485921, 2820302411,
    3259730800, 3345764771, 3516065817, 3600352804, 4094571909, 275423344,
    430227734, 506948616, 659060556, 883997877, 958139571, 1322822218,
    1537002063, 1747873779, 1955562222, 2024104815, 2227730452, 2361852424,
    2428436474, 2756734187, 3204031479, 3329325298,
  ];
  const H = [
    1779033703, 3144134277, 1013904242, 2773480762, 1359893119, 2600822924,
    528734635, 1541459225,
  ];
  const bytes = Array.from(new TextEncoder().encode(message));
  const bitLen = bytes.length * 8;
  bytes.push(128);
  while (bytes.length % 64 !== 56) bytes.push(0);
  const high = Math.floor(bitLen / 4294967296),
    low = bitLen >>> 0;
  [high, low].forEach((v) => {
    bytes.push((v >>> 24) & 255, (v >>> 16) & 255, (v >>> 8) & 255, v & 255);
  });
  const r = (x, n) => (x >>> n) | (x << (32 - n));
  for (let offset = 0; offset < bytes.length; offset += 64) {
    const w = new Array(64);
    for (let i = 0; i < 16; i++)
      w[i] =
        (bytes[offset + i * 4] << 24) |
        (bytes[offset + i * 4 + 1] << 16) |
        (bytes[offset + i * 4 + 2] << 8) |
        bytes[offset + i * 4 + 3];
    for (let i = 16; i < 64; i++) {
      const s0 = r(w[i - 15], 7) ^ r(w[i - 15], 18) ^ (w[i - 15] >>> 3),
        s1 = r(w[i - 2], 17) ^ r(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0;
    }
    let [a, b, c, d, e, f, g, h] = H;
    for (let i = 0; i < 64; i++) {
      const S1 = r(e, 6) ^ r(e, 11) ^ r(e, 25),
        ch = (e & f) ^ (~e & g),
        t1 = (h + S1 + ch + K[i] + w[i]) | 0,
        S0 = r(a, 2) ^ r(a, 13) ^ r(a, 22),
        maj = (a & b) ^ (a & c) ^ (b & c),
        t2 = (S0 + maj) | 0;
      h = g;
      g = f;
      f = e;
      e = (d + t1) | 0;
      d = c;
      c = b;
      b = a;
      a = (t1 + t2) | 0;
    }
    [a, b, c, d, e, f, g, h].forEach((v, i) => (H[i] = (H[i] + v) | 0));
  }
  return H.map((v) => (v >>> 0).toString(16).padStart(8, "0")).join("");
}
