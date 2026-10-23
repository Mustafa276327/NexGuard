use sha2::{Digest, Sha256};
use std::collections::HashMap;

#[no_mangle]
pub extern "C" fn sha256_bytes(input: *const u8, len: usize, out: *mut u8) {
    if input.is_null() || out.is_null() { return; }
    let slice = unsafe { std::slice::from_raw_parts(input, len) };
    let mut hasher = Sha256::new();
    hasher.update(slice);
    let res = hasher.finalize();
    unsafe { std::ptr::copy_nonoverlapping(res.as_ptr(), out, 32); }
}

/// Shannon entropy * 1000 (integer) for a byte slice.
#[no_mangle]
pub extern "C" fn shannon_entropy_x1000(input: *const u8, len: usize) -> u32 {
    if input.is_null() || len == 0 { return 0; }
    let slice = unsafe { std::slice::from_raw_parts(input, len) };
    let mut freq = [0u64; 256];
    for &b in slice { freq[b as usize] += 1; }
    let n = len as f64;
    let mut entropy = 0.0f64;
    for &c in &freq {
        if c == 0 { continue; }
        let p = c as f64 / n;
        entropy -= p * p.log2();
    }
    (entropy * 1000.0) as u32
}

/// Returns 1 if buffer contains the given needle.
#[no_mangle]
pub extern "C" fn contains_needle(input: *const u8, len: usize, needle: *const u8, nlen: usize) -> i32 {
    if input.is_null() || needle.is_null() || nlen == 0 || len < nlen { return 0; }
    let haystack = unsafe { std::slice::from_raw_parts(input, len) };
    let needle = unsafe { std::slice::from_raw_parts(needle, nlen) };
    for i in 0..=(len - nlen) {
        if &haystack[i..i + nlen] == needle { return 1; }
    }
    0
}

/// Count occurrences of a needle (capped).
#[no_mangle]
pub extern "C" fn count_needle(input: *const u8, len: usize, needle: *const u8, nlen: usize) -> u32 {
    if input.is_null() || needle.is_null() || nlen == 0 || len < nlen { return 0; }
    let haystack = unsafe { std::slice::from_raw_parts(input, len) };
    let needle = unsafe { std::slice::from_raw_parts(needle, nlen) };
    let mut count = 0u32;
    let mut i = 0;
    while i <= len - nlen {
        if &haystack[i..i + nlen] == needle { count += 1; i += nlen; } else { i += 1; }
        if count > 10000 { break; }
    }
    count
}

/// Magic detection: returns code (0 unknown, 1 zip, 2 pdf, 3 pe, 4 elf, 5 dex, 6 macho).
#[no_mangle]
pub extern "C" fn detect_magic(input: *const u8, len: usize) -> i32 {
    if input.is_null() || len < 4 { return 0; }
    let s = unsafe { std::slice::from_raw_parts(input, len) };
    if s[0] == 0x50 && s[1] == 0x4B { return 1; } // zip
    if s[0] == 0x25 && s[1] == 0x50 && s[2] == 0x44 && s[3] == 0x46 { return 2; } // pdf
    if s[0] == 0x4D && s[1] == 0x5A { return 3; } // PE
    if s[0] == 0x7F && s[1] == 0x45 && s[2] == 0x4C && s[3] == 0x46 { return 4; } // ELF
    if len >= 8 && &s[0..8] == b"dex\n035\x00" { return 5; }
    if s[0] == 0xCF && s[1] == 0xFA && s[2] == 0xED && s[3] == 0xFE { return 6; }
    0
}
