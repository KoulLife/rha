type AppHeaderProps = {
  unreadCount: number;
  accountMenuOpen: boolean;
  onNotificationClick: () => void;
  onAccountToggle: () => void;
  onLogout: () => void;
};

function AppHeader({ unreadCount, accountMenuOpen, onNotificationClick, onAccountToggle, onLogout }: AppHeaderProps) {
  return (
    <header className="topbar">
      <button className="text-button" type="button" onClick={onNotificationClick}>
        알림 <span className="badge warning">{unreadCount}</span>
      </button>
      <div className="account-area">
        <button className="user account-trigger" type="button" aria-expanded={accountMenuOpen} onClick={onAccountToggle}>
          <span className="avatar">홍</span>
          <span>홍길동</span>
          <span className="account-chevron" aria-hidden="true">⌄</span>
        </button>
        {accountMenuOpen && (
          <div className="account-menu" role="menu">
            <button type="button" role="menuitem" onClick={onLogout}>로그아웃</button>
          </div>
        )}
      </div>
    </header>
  );
}

export { AppHeader };
