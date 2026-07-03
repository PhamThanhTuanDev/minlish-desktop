package com.minlish.desktop;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.UINT;
import com.sun.jna.platform.win32.ShellAPI.APPBARDATA;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Window;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;

public class WindowsAppBarHelper {

    private static final int ABM_NEW = 0x00000000;
    private static final int ABM_REMOVE = 0x00000001;
    private static final int ABM_QUERYPOS = 0x00000002;
    private static final int ABM_SETPOS = 0x00000003;
    private static final int ABM_ACTIVATE = 0x00000006;

    private static final HWND HWND_TOP = new HWND(Pointer.createConstant(0));
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_SHOWWINDOW = 0x0040;

    public static final int ABE_LEFT = 0;
    public static final int ABE_TOP = 1;
    public static final int ABE_RIGHT = 2;
    public static final int ABE_BOTTOM = 3;

    private HWND hwnd;
    private boolean registered;
    private Insets cachedExternalInsets;

    public void registerAppBar(Window window) {
        registerAppBar(window, ABE_BOTTOM);
    }

    public void registerAppBar(Window window, int edge) {
        registerAppBar(window, edge, Math.max(1, window == null ? 1 : window.getHeight()));
    }

    public void registerAppBar(Window window, int edge, int thickness) {
        if (window == null) {
            return;
        }

        HWND targetHwnd = new HWND(Native.getComponentPointer(window));

        if (registered && targetHwnd.equals(hwnd)) {
            dock(window, edge, thickness);
            return;
        }

        if (registered) {
            unregisterAppBar();
        }

        hwnd = targetHwnd;
        cachedExternalInsets = null;

        APPBARDATA abd = new APPBARDATA();
        abd.cbSize = new DWORD(abd.size());
        abd.hWnd = hwnd;

        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_NEW), abd);
        registered = true;

        dock(window, edge, thickness);
    }

    public void dock(Window window, int edge) {
        dock(window, edge, Math.max(1, window == null ? 1 : window.getHeight()));
    }

    public void dock(Window window, int edge, int thickness) {
        if (window == null) {
            return;
        }

        if (!registered || hwnd == null) {
            registerAppBar(window, edge, thickness);
            return;
        }

        GraphicsConfiguration configuration = window.getGraphicsConfiguration();
        if (configuration == null) {
            configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
        }
        
        // TÍNH TOÁN TỶ LỆ SCALE (DPI SCALING) CỦA MÀN HÌNH ĐỂ TRÁNH LỖI OVERLAP
        AffineTransform t = configuration.getDefaultTransform();
        double scaleX = t.getScaleX();
        double scaleY = t.getScaleY();

        if (cachedExternalInsets == null) {
            cachedExternalInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        }
        Insets logicalInsets = cachedExternalInsets;
        
        int logicalScreenWidth = configuration.getBounds().width;
        int logicalScreenHeight = configuration.getBounds().height;

        // CHUYỂN TỪ LOGICAL SANG PHYSICAL (ĐỂ WINDOWS CHẤP NHẬN ĐÂY LÀ THANH BAR TOÀN MÀN HÌNH)
        int physScreenWidth = (int) Math.round(logicalScreenWidth * scaleX);
        int physScreenHeight = (int) Math.round(logicalScreenHeight * scaleY);
        
        int physInsetTop = (int) Math.round(logicalInsets.top * scaleY);
        int physInsetBottom = (int) Math.round(logicalInsets.bottom * scaleY);

        int physThickness = (int) Math.round(Math.max(1, thickness) * scaleY);
        int physFullWidth = (int) Math.round(Math.max(1, window.getWidth()) * scaleX);

        boolean horizontalEdge = (edge == ABE_TOP || edge == ABE_BOTTOM);
        
        // Bắt buộc dùng physScreenWidth để thanh bar chiếm TRỌN chiều rộng vật lý.
        // Khi đó Windows mới bắt đầu tính toán và thu nhỏ (push) các ứng dụng khác.
        int physDockWidth = horizontalEdge ? physScreenWidth : physFullWidth;
        int physDockX = 0;

        APPBARDATA abd = new APPBARDATA();
        abd.cbSize = new DWORD(abd.size());
        abd.hWnd = hwnd;
        abd.uEdge = new UINT(edge);

        RECT rect = new RECT();
        switch (edge) {
            case ABE_TOP:
                rect.left = physDockX;
                rect.right = physDockX + physDockWidth;
                rect.top = physInsetTop;
                rect.bottom = rect.top + physThickness;
                break;
            case ABE_BOTTOM:
                rect.left = physDockX;
                rect.right = physDockX + physDockWidth;
                rect.bottom = physScreenHeight - physInsetBottom;
                rect.top = rect.bottom - physThickness;
                break;
            case ABE_LEFT:
                rect.top = 0;
                rect.bottom = physScreenHeight;
                rect.left = 0;
                rect.right = physThickness;
                break;
            case ABE_RIGHT:
            default:
                rect.top = 0;
                rect.bottom = physScreenHeight;
                rect.right = physScreenWidth;
                rect.left = physScreenWidth - physThickness;
                break;
        }
        abd.rc = rect;

        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_QUERYPOS), abd);

        switch (edge) {
            case ABE_TOP:
                abd.rc.bottom = abd.rc.top + physThickness;
                break;
            case ABE_BOTTOM:
                abd.rc.top = abd.rc.bottom - physThickness;
                break;
            case ABE_LEFT:
                abd.rc.right = abd.rc.left + physThickness;
                break;
            case ABE_RIGHT:
                abd.rc.left = abd.rc.right - physThickness;
                break;
        }

        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_SETPOS), abd);

        int finalPhysX = abd.rc.left;
        int finalPhysY = abd.rc.top;
        int finalPhysWidth = Math.max(1, abd.rc.right - abd.rc.left);
        int finalPhysHeight = Math.max(1, abd.rc.bottom - abd.rc.top);

        User32.INSTANCE.SetWindowPos(hwnd, HWND_TOP, finalPhysX, finalPhysY, finalPhysWidth, finalPhysHeight,
                SWP_NOACTIVATE | SWP_NOZORDER | SWP_SHOWWINDOW);

        // CHUYỂN NGƯỢC TỪ PHYSICAL VỀ LOGICAL ĐỂ JAVA SWING RENDER CHUẨN
        int logicalX = (int) Math.round(finalPhysX / scaleX);
        int logicalY = (int) Math.round(finalPhysY / scaleY);
        int logicalWidth = (int) Math.round(finalPhysWidth / scaleX);
        int logicalHeight = (int) Math.round(finalPhysHeight / scaleY);

        window.setLocation(logicalX, logicalY);
        window.setSize(logicalWidth, logicalHeight);

        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_ACTIVATE), abd);
    }

    public void unregisterAppBar() {
        if (hwnd != null && registered) {
            APPBARDATA abd = new APPBARDATA();
            abd.cbSize = new DWORD(abd.size());
            abd.hWnd = hwnd;
            Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_REMOVE), abd);
        }
        registered = false;
        hwnd = null;
        cachedExternalInsets = null;
    }
}