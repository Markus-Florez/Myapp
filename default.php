<?php
// Configuración de la URL de descarga
$apkUrl = "https://mycar.adsosena.site/app-debug.apk";
$logoUrl = "https://mycar.adsosena.site/logo-mecatronica-yv.png";
?>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Mecatrónica Y&V</title>
    <style>
        body {
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            /* Degradado azul a verde como en la imagen */
            background: linear-gradient(180deg, #5c9ae6 0%, #80cba3 100%);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            color: white;
        }
        .header {
            text-align: center;
            margin-bottom: 40px;
            z-index: 1;
        }
        .header h1 {
            font-size: 28px;
            font-weight: 700;
            margin: 0;
            letter-spacing: 0.5px;
        }
        .header p {
            font-size: 16px;
            margin: 8px 0 0;
            font-weight: 400;
            opacity: 0.9;
        }
        .card {
            background: white;
            width: 85%;
            max-width: 340px;
            border-radius: 20px;
            padding: 60px 24px 32px;
            text-align: center;
            box-shadow: 0 8px 20px rgba(0,0,0,0.1);
            position: relative;
            margin-bottom: 20px;
            color: #555;
        }
        .logo-container {
            width: 80px;
            height: 80px;
            background: white;
            border-radius: 12px;
            padding: 8px;
            box-shadow: 0 4px 10px rgba(0,0,0,0.15);
            display: flex;
            align-items: center;
            justify-content: center;
            position: absolute;
            top: -40px;
            left: 50%;
            transform: translateX(-50%);
            z-index: 10;
        }
        .logo-container img {
            width: 100%;
            height: 100%;
            object-fit: contain;
        }
        .card p {
            font-size: 18px;
            line-height: 1.5;
            margin: 0;
            color: #666;
        }
        .link-btn {
            background: white;
            width: 85%;
            max-width: 340px;
            border-radius: 16px;
            padding: 16px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            text-decoration: none;
            box-shadow: 0 4px 10px rgba(0,0,0,0.1);
            transition: transform 0.2s;
            margin-bottom: 16px;
            box-sizing: border-box;
        }
        .link-btn:active {
            transform: scale(0.98);
        }
        .link-btn .icon {
            color: #999;
            display: flex;
            align-items: center;
            margin-right: 12px;
        }
        .link-btn .text {
            flex: 1;
            text-align: left;
            color: #2196F3;
            font-weight: 500;
            font-size: 16px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .link-btn .arrow {
            color: #999;
            display: flex;
            align-items: center;
        }
        .download-action-btn {
            background: #00C853;
            color: white;
            width: 85%;
            max-width: 340px;
            padding: 16px;
            border-radius: 16px;
            text-align: center;
            text-decoration: none;
            font-weight: bold;
            font-size: 18px;
            box-shadow: 0 4px 10px rgba(0,0,0,0.2);
            transition: background 0.2s;
            box-sizing: border-box;
            display: block;
        }
        .download-action-btn:hover {
            background: #00E676;
        }
    </style>
</head>
<body>

    <div class="header">
        <h1>Mecatrónica y&v</h1>
        <p>Markus Florez</p>
    </div>

    <div class="card">
        <div class="logo-container">
            <img src="<?php echo $logoUrl; ?>" alt="Logo" onerror="this.src='https://via.placeholder.com/80?text=Logo'">
        </div>
        <p>es una aplicación para Android, sobre repositorios de diagramas vehiculares</p>
    </div>

    <!-- Botón estilo enlace (tarjeta blanca) -->
    <a href="<?php echo $apkUrl; ?>" class="link-btn" download>
        <div class="icon">
            <!-- Icono de mundo/web -->
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>
        </div>
        <div class="text">micoche.adsosena.site</div>
        <div class="arrow">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
        </div>
    </a>

    <!-- Botón de Descarga Directa -->
    <a href="<?php echo $apkUrl; ?>" class="download-action-btn" download>
        DESCARGAR APK
    </a>

</body>
</html>
