function firstName(fullName: string) {
  return fullName.trim().split(/\s+/)[0] ?? fullName;
}

export function DashboardHeader({ fullName }: { fullName: string }) {
  return (
    <div>
      <h1 className="text-2xl font-bold text-foreground">Hi, {firstName(fullName)} 👋</h1>
      <p className="text-sm text-muted-foreground">Build your skills. Show what you can do.</p>
      <p className="text-sm text-muted-foreground">Discover opportunities.</p>
    </div>
  );
}
