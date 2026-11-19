import json
import urllib.request
import flet as ft

# உங்கள் Cloud Backend URL (Render / Railway / PC IP)
BACKEND_URL = "http://127.0.0.1:8000/ask"


def main(page: ft.Page):
    page.title = "AKILA Voice Assistant"
    page.theme_mode = ft.ThemeMode.DARK
    page.bgcolor = "#0f172a"
    page.padding = 16

    chat_history = ft.ListView(expand=True, spacing=10, auto_scroll=True)
    status_text = ft.Text(
        "AKILA பேசத் தயாராக உள்ளது...",
        size=13,
        color=ft.Colors.BLUE_200,
    )

    prompt_input = ft.TextField(
        hint_text="தமிழ், English அல்லது Tanglish-ல் கேளுங்கள்...",
        expand=True,
        border_radius=24,
        on_submit=lambda e: send_message(),
    )

    def add_bubble(sender: str, text: str, is_user: bool):
        chat_history.controls.append(
            ft.Container(
                content=ft.Column(
                    [
                        ft.Text(sender, size=11, weight=ft.FontWeight.BOLD, color=ft.Colors.GREY_400),
                        ft.Text(text, size=15, color=ft.Colors.WHITE),
                    ],
                    spacing=4,
                ),
                padding=12,
                border_radius=14,
                bgcolor=ft.Colors.BLUE_900 if is_user else ft.Colors.GREY_900,
            )
        )
        page.update()

    def send_message():
        user_text = (prompt_input.value or "").strip()
        if not user_text:
            return

        prompt_input.value = ""
        add_bubble("நீங்கள்", user_text, is_user=True)
        status_text.value = "AKILA சிந்திக்கிறது..."
        page.update()

        try:
            payload = json.dumps({"text": user_text, "language": "ta"}).encode("utf-8")
            req = urllib.request.Request(
                BACKEND_URL,
                data=payload,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=20) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                reply = data.get("reply", "")
        except Exception as exc:
            reply = f"Server இணைப்பில் பிழை: {exc}"

        add_bubble("AKILA", reply, is_user=False)
        status_text.value = "AKILA பேசத் தயாராக உள்ளது..."
        page.update()

    send_button = ft.IconButton(
        icon=ft.Icons.SEND,
        icon_size=26,
        icon_color=ft.Colors.BLUE_300,
        on_click=lambda e: send_message(),
    )

    page.add(
        ft.SafeArea(
            expand=True,
            content=ft.Column(
                [
                    ft.Text("AKILA Assistant", size=24, weight=ft.FontWeight.BOLD, color=ft.Colors.WHITE),
                    status_text,
                    ft.Divider(height=12, color=ft.Colors.GREY_800),
                    chat_history,
                    ft.Row([prompt_input, send_button], spacing=8),
                ],
                expand=True,
            ),
        )
    )


if __name__ == "__main__":
    ft.run(main)
