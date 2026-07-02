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

public class WindowsAppBarHelper {

    private static final double DOCK_WIDTH_RATIO = 0.90;

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

    public void registerAppBar(Window window) {
        registerAppBar(window, ABE_BOTTOM);
    }

    public void registerAppBar(Window window, int edge) {
        if (window == null) {
            return;
        }

        hwnd = new HWND(Native.getComponentPointer(window));
        registered = true;

        APPBARDATA abd = new APPBARDATA();
        abd.cbSize = new DWORD(abd.size());
        abd.hWnd = hwnd;

        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_NEW), abd);
        dock(window, edge);
    }

    public void dock(Window window, int edge) {
        if (window == null) {
            return;
        }

        if (hwnd == null) {
            registerAppBar(window, edge);
            return;
        }

        APPBARDATA abd = new APPBARDATA();
        abd.cbSize = new DWORD(abd.size());
        abd.hWnd = hwnd;
        abd.uEdge = new UINT(edge);

        int height = Math.max(1, window.getHeight());
        int width = Math.max(1, window.getWidth());
        GraphicsConfiguration configuration = window.getGraphicsConfiguration();
        if (configuration == null) {
            configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        }

        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        int screenWidth = configuration.getBounds().width;
        int screenHeight = configuration.getBounds().height;
        int dockWidth = Math.min(width, (int) Math.round(screenWidth * DOCK_WIDTH_RATIO));
        int dockX = edge == ABE_TOP
            ? 0
            : (screenWidth > dockWidth ? (screenWidth - dockWidth) / 2 : 0);

        RECT rect = new RECT();
        rect.left = dockX;
        rect.top = 0;
        rect.right = dockX + dockWidth;
        rect.bottom = screenHeight;

        if (edge == ABE_TOP) {
            rect.top = screenInsets.top;
            rect.bottom = rect.top + height;
        } else if (edge == ABE_BOTTOM) {
            rect.top = screenHeight - screenInsets.bottom - height;
            rect.bottom = rect.top + height;
        } else if (edge == ABE_LEFT) {
            rect.right = rect.left + width;
        } else if (edge == ABE_RIGHT) {
            rect.left = rect.right - width;
        }

        abd.rc = rect;

        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_QUERYPOS), abd);
        if (edge == ABE_TOP) {
            abd.rc.top = screenInsets.top;
            abd.rc.bottom = abd.rc.top + height;
        } else if (edge == ABE_BOTTOM) {
            abd.rc.top = screenHeight - screenInsets.bottom - height;
            abd.rc.bottom = abd.rc.top + height;
        } else if (edge == ABE_LEFT) {
            abd.rc.right = abd.rc.left + width;
        } else if (edge == ABE_RIGHT) {
            abd.rc.left = abd.rc.right - width;
        }

        User32.INSTANCE.SetWindowPos(hwnd, HWND_TOP, abd.rc.left, abd.rc.top,
                abd.rc.right - abd.rc.left, abd.rc.bottom - abd.rc.top,
                SWP_NOACTIVATE | SWP_NOZORDER | SWP_SHOWWINDOW);
        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_SETPOS), abd);

        window.setLocation(abd.rc.left, abd.rc.top);
        window.setSize(abd.rc.right - abd.rc.left, abd.rc.bottom - abd.rc.top);
        Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_ACTIVATE), abd);
    }

    public void unregisterAppBar() {
        if (hwnd != null && registered) {
            APPBARDATA abd = new APPBARDATA();
            abd.cbSize = new DWORD(abd.size());
            abd.hWnd = hwnd;
            Shell32.INSTANCE.SHAppBarMessage(new DWORD(ABM_REMOVE), abd);
            registered = false;
        }
    }
}