use sha2::{Digest, Sha256};

#[no_mangle]
pub extern "C" fn sha256_bytes(input: *const u8, len: usize, out: *mut u8) {
    if input.is_null() || out.is_null() { return; }
    let slice = unsafe { std::slice::from_raw_parts(input, len) };
    let mut hasher = Sha256::new();
    hasher.update(slice);
    let res = hasher.finalize();
    unsafe { std::ptr::copy_nonoverlapping(res.as_ptr(), out, 32); }
}