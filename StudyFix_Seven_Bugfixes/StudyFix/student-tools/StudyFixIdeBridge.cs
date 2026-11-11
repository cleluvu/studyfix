using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Management;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;

// Local, read-only foreground categories. Never returns titles, paths or clipboard data.
public static class StudyFixIdeBridge {
    [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint process);
    static readonly object Gate = new object();
    static readonly List<Change> Changes = new List<Change>();
    static readonly string Boot = Guid.NewGuid().ToString("N");
    static long sequence, sampledAt;
    static string kind = "UNKNOWN";
    static int sampling;
    static int cachedPid; static long cachedStart; static string cachedKind;
    class Change { public long Seq, At; public string Kind; }
    static long Now() { return (long)(DateTime.UtcNow-new DateTime(1970,1,1)).TotalMilliseconds; }
    public static string Classify(string name, string commandLine) {
        name = (name ?? "").ToLowerInvariant();
        if(name=="code" || name=="code.exe") return "VSCODE";
        if(Regex.IsMatch(name,@"^netbeans(?:64)?(?:\.exe)?$")) return "NETBEANS";
        if(Regex.IsMatch(name,@"^javaw?(?:\.exe)?$") && Regex.IsMatch(commandLine ?? "",@"(?:^|\s)(?:-Dnetbeans\.home=|org\.netbeans\.Main(?:\s|$))")) return "NETBEANS";
        if(Regex.IsMatch(name,@"^(?:chrome|msedge|firefox|brave|opera|vivaldi|coccoc)(?:\.exe)?$")) return "BROWSER";
        return "OTHER";
    }
    static string Foreground() {
        try {
            IntPtr window=GetForegroundWindow(); if(window==IntPtr.Zero)return "UNKNOWN";
            uint id; GetWindowThreadProcessId(window,out id);
            using(Process process=Process.GetProcessById((int)id)) {
                long started=process.StartTime.ToUniversalTime().Ticks;
                if(cachedPid==(int)id && cachedStart==started)return cachedKind;
                string command="", name=process.ProcessName;
                if(name.Equals("java",StringComparison.OrdinalIgnoreCase)||name.Equals("javaw",StringComparison.OrdinalIgnoreCase))
                    using(ManagementObject record=new ManagementObject("Win32_Process.Handle='"+id+"'")) { record.Get();command=Convert.ToString(record["CommandLine"]); }
                cachedPid=(int)id; cachedStart=started; cachedKind=Classify(name,command);return cachedKind;
            }
        } catch { return "UNKNOWN"; }
    }
    static void Sample(object unused) {
        if(Interlocked.Exchange(ref sampling,1)!=0)return;
        try { string next=Foreground(); long now=Now();
        lock(Gate) { sampledAt=now; if(next!=kind) {kind=next;Changes.Add(new Change{Seq=++sequence,At=now,Kind=next});if(Changes.Count>600)Changes.RemoveAt(0);} }
        } finally { Interlocked.Exchange(ref sampling,0); }
    }
    static string Snapshot(long since) {
        lock(Gate) {
            var json=new StringBuilder("{\"protocol\":1,\"boot\":\""+Boot+"\",\"sequence\":"+sequence+",\"sampledAt\":"+sampledAt+",\"kind\":\""+kind+"\",\"truncated\":"+((Changes.Count>0 && since>0 && since<Changes[0].Seq-1)?"true":"false")+",\"changes\":[");
            bool comma=false;foreach(Change item in Changes)if(item.Seq>since){if(comma)json.Append(',');comma=true;json.Append("{\"sequence\":"+item.Seq+",\"at\":"+item.At+",\"kind\":\""+item.Kind+"\"}");}return json.Append("]}").ToString();
        }
    }
    public static void Run(string origin,int port) {
        var listener=new TcpListener(IPAddress.Loopback,port);listener.Start();
        using(var timer=new Timer(Sample,null,0,150))try {
            Console.WriteLine("StudyFix IDE support: "+origin+" (VS Code / NetBeans). Ctrl+C to stop.");
            while(true)using(TcpClient client=listener.AcceptTcpClient()) {
                client.ReceiveTimeout=1500;client.SendTimeout=1500;
                try { using(NetworkStream stream=client.GetStream()) {
                    var reader=new StreamReader(stream,Encoding.ASCII,false,1024,true);string first=reader.ReadLine(),requestOrigin="",host="";int length=0;string line;
                    while(!string.IsNullOrEmpty(line=reader.ReadLine())) {length+=line.Length;if(length>8192)throw new IOException();int colon=line.IndexOf(':');if(colon>0){string key=line.Substring(0,colon).Trim(),value=line.Substring(colon+1).Trim();if(key.Equals("Origin",StringComparison.OrdinalIgnoreCase))requestOrigin=value;if(key.Equals("Host",StringComparison.OrdinalIgnoreCase))host=value;}}
                    string[] request=(first??"").Split(' ');bool trusted=requestOrigin==origin&&host=="127.0.0.1:"+port;
                    bool valid=request.Length==3&&Regex.IsMatch(request[1],@"^/status\?since=\d+$");bool preflight=request.Length==3&&request[0]=="OPTIONS";
                    int status=!trusted?403:!valid?404:request[0]!="GET"&&!preflight?405:preflight?204:200;
                    string body="{}";if(status==200){long since;if(!long.TryParse(request[1].Substring(14),out since))status=400;else body=Snapshot(since);}
                    byte[] bytes=status==204?new byte[0]:Encoding.UTF8.GetBytes(body);
                    string headers="HTTP/1.1 "+status+" Result\r\nContent-Type: application/json\r\nContent-Length: "+bytes.Length+"\r\nConnection: close\r\nCache-Control: no-store\r\n";
                    if(trusted)headers+="Access-Control-Allow-Origin: "+origin+"\r\nVary: Origin\r\nAccess-Control-Allow-Methods: GET, OPTIONS\r\nAccess-Control-Allow-Private-Network: true\r\n";
                    byte[] head=Encoding.ASCII.GetBytes(headers+"\r\n");stream.Write(head,0,head.Length);stream.Write(bytes,0,bytes.Length);
                }}catch(IOException){}catch(SocketException){}
            }
        }finally{listener.Stop();}
    }
}