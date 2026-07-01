package com.minlish.desktop;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.UINT;
import com.sun.jna.platform.win32.ShellAPI.APPBARDATA;
import java.awt.Window;

public class WindowsAppBarHelper {

    private static final int ABM_NEW = 0x00000000;
    private static final int ABM_REMOVE = 0x00000001;
    private static final int ABM_SETPOS = 0x00000003;
    private static final int ABE_BOTTOM = 3;

    private HWND hwnd;

    public void registerAppBar(Window window) {
        // Lấy Handle (HWND) của cửa sổ Java
        hwnd = new HWND(Native.getComponentPointer(window));

        APPBARDATA abd = new APPBARDATA();
        // Bọc kích thước bằng DWORD
        abd.cbSize = new DWORD(abd.size());
        abd.hWnd = hwnd;

        // Đăng ký cửa sổ làm AppBar với Windows (Bọc tham số bằng DWORD)
        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_NEW), abd);

        // Đặt vị trí AppBar ở dưới cùng màn hình (Bọc tham số bằng UINT)
        abd.uEdge = new UINT(ABE_BOTTOM);
        
        RECT rect = new RECT();
        rect.left = 0;
        rect.right = User32.INSTANCE.GetSystemMetrics(User32.SM_CXSCREEN);
        rect.bottom = User32.INSTANCE.GetSystemMetrics(User32.SM_CYSCREEN);
        // Chiều cao tính toán dựa trên cửa sổ Swing
        rect.top = rect.bottom - window.getHeight(); 
        abd.rc = rect;

        // Báo cho Windows ép các cửa sổ khác nhường chỗ
        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_SETPOS), abd);
    }

    public void unregisterAppBar() {
        if (hwnd != null) {
            APPBARDATA abd = new APPBARDATA();
            abd.cbSize = new DWORD(abd.size());
            abd.hWnd = hwnd;
            // Hủy đăng ký để Windows trả lại không gian hiển thị bình thường
            Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_REMOVE), abd);
        }
    }
}