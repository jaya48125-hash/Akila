import httpx
import flet as ft

# உங்கள் Cloud Backend URL (Render / Railway / PC IP)
BACKEND_URL = "http://127.0.0.1:8000/ask"


def main(page: ft.Page):
    page.title = "AKILA Voice Assistant"
    page.theme_mode = ft.ThemeMode.DARK
    page.padding = 20
    page.horizontal_alignment = ft.CrossAxisAlignment.CENTER

    chat_history = ft.ListView(expand=True, spacing=12, auto_scroll=True)
    status_text = ft.Text("AKILA பேசத் தயாராக உள்ளது...", size=13, color=ft.Colors.BLUE_200)

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
                        ft.Text(text, size=15),
                    ],
                    spacing=4,
                ),
                padding=14,
                border_radius=16,
                bgcolor=ft.Colors.BLUE_900 if is_user else ft.Colors.GREY_900,
                alignment=ft.Alignment.CENTER_RIGHT if is_user else ft.Alignment.CENTER_LEFT,
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
            with httpx.Client(timeout=30.0) as client:
                res = client.post(BACKEND_URL, json={"text": user_text, "language": "ta"})
                res.raise_for_status()
                reply = res.json().get("reply", "")
        except Exception as exc:
            reply = f"Server இணைப்பில் பிழை: {exc}"

        add_bubble("AKILA", reply, is_user=False)
        status_text.value = "AKILA பேசத் தயாராக உள்ளது..."
        page.update()

    mic_button = ft.IconButton(
        icon=ft.Icons.MIC,
        icon_size=32,
        bgcolor=ft.Colors.BLUE_600,
        icon_color=ft.Colors.WHITE,
        tooltip="AKILA Voice Input",
        on_click=lambda e: send_message(),
    )

    page.add(
        ft.Text("AKILA Assistant", size=24, weight=ft.FontWeight.BOLD),
        status_text,
        ft.Divider(height=16, color=ft.Colors.GREY_800),
        chat_history,
        ft.Row([prompt_input, mic_button], spacing=10),
    )


if __name__ == "__main__":
    ft.run(main)
